# Admin & 신규 기능 정리 (관리자 시스템 + 최근 변경 사항)

> 본 문서는 관리자(Admin) 기능과 함께, 이번에 백엔드/프론트에서 변경·추가된 사항을 정리합니다.
> 최종 목표: **프론트 관리자 페이지(관리자 시스템)는 공지 관리만 커스텀 API를 쓰고, 나머지(모니터링/로그)는 Actuator를 최대한 그대로 활용한다.**

---

## 1. 개요

| 항목 | 결정 사항 |
|------|-----------|
| 공지 저장 | `system_config.notices` (JSON) → **전용 `notice` 테이블로 이전** |
| 공지 등록 시 알림 | 기존 PGMQ → FCM 파이프라인 그대로 사용 (전체 사용자 broadcast) |
| 모니터링/로그 | **Spring Boot Actuator 활용** (health, metrics, loggers, logfile) |
| 도메인 기능 플래그 | ❌ 구현 안 함 |
| 멤버 관리 | ❌ DB 직접 접속으로 처리 |
| 문의/제보 관리 | ❌ 카카오톡 오픈채팅으로 처리 |
| 로그 파일 | **prod에서만 생성** (dev는 콘솔만) |

---

## 2. 백엔드 변경 사항

### 2.1 Actuator 보안 강화 (중요)

**변경 전 문제**: `/actuator/**`가 `PUBLIC_URLS`에 등록되어 있어 **인증 없이** `env`, `heapdump` 등 전체 엔드포인트 접근 가능 + `exposure: "*"`로 전부 노출.

**변경 후**:

| 항목 | 변경 내용 |
|------|-----------|
| 노출 엔드포인트 | `health, info, metrics, loggers, logfile, threaddump` 만 노출 (`"*"` 제거) |
| 접근 제어 | `/actuator/**` 는 **ADMIN 역할만** 접근 가능 |
| health 상세 | `show-details: when_authorized` + `roles: ADMIN` |

**수정 파일**:

| 파일 | 내용 |
|------|------|
| `global/security/GlobalSecurityConfig.java` | `PUBLIC_URLS`에서 `/actuator/**` 제거, secured 체인에 `/actuator/**` matcher + `hasRole('ADMIN')` 추가 |
| `global/security/filter/JwtAuthenticationFilter.java` | `/actuator/` 경로도 JWT 인증 처리하도록 `shouldNotFilter` 수정 |
| `global/security/filter/ConsentCheckFilter.java` | `/actuator/` 를 개인정보 동의 예외 경로에 추가 |
| `application-dev.yml`, `application-prod.yml` | `management.endpoints.web.exposure.include` 축소 + health 상세 설정 |

> admin JWT(쿠키)가 있어야 `/actuator/*` 접근 가능. CORS는 기존 설정(`localhost:5173`) 그대로 적용.

### 2.2 공지 도메인 신설 (notice)

**이전 구조**: `system_config` 테이블의 `notices` 키에 JSON 배열로 저장 → "새 공지 추가"를 감지할 수 없어 등록 시 푸시 발송 불가, id/createdAt 없음.

**새 구조**: 전용 테이블 + CRUD.

```sql
CREATE TABLE IF NOT EXISTS notice (
    id         BIGSERIAL    PRIMARY KEY,
    type       VARCHAR(20)  NOT NULL,   -- critical | update | general
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
```

- dev/test: JPA `ddl-auto: update` 가 자동 생성
- prod: `schema-prod.sql` 에 `20. notice` 섹션 추가됨 (`ddl-auto: validate` 대응)
- 기존 `SettingDefinition.NOTICES` 제거, `SystemConfigService.initDefaultConfigs()` 에서 잔여 `notices` 행 자동 정리

**신규 파일**:

| 파일 | 역할 |
|------|------|
| `database/entity/NoticeEntity.java` | 엔티티 |
| `database/repository/NoticeRepository.java` | `findAllByOrderByCreatedAtDesc()` |
| `notice/dto/NoticeRequest.java` | 생성/수정 공용 요청 (type/title/content) |
| `notice/dto/NoticeResponse.java` | 응답 (id/type/title/content/createdAt) |
| `notice/service/NoticeService.java` | CRUD + broadcast |
| `notice/controller/NoticeController.java` | API |
| `database/repository/MemberDeviceRepository.java` | `findAllFcmTokens()` 추가 (broadcast용) |
| `global/api/code/ErrorCode.java` | `NOTICE_NOT_FOUND` 추가 |
| `schema-prod.sql` | notice 테이블 DDL |

**API 명세**:

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| `GET` | `/api/1/notices` | 공개 | 공지 목록 최신순 |
| `POST` | `/api/1/notices` | ADMIN | 등록 + **전체 사용자 푸시 발송** |
| `PUT` | `/api/1/notices/{id}` | ADMIN | 수정 |
| `DELETE` | `/api/1/notices/{id}` | ADMIN | 삭제 |

**등록 시 푸시 규약** (기존 `fcm_token_strategy.md` 의 producer 규약 그대로):

```json
{
  "token": "<fcm_token>",
  "notification": {
    "title": "공지 알림",
    "body": "<공지 제목>"
  },
  "data": {
    "type": "SYSTEM_NOTICE",
    "path": "/",
    "timestamp": 1710000000000
  }
}
```

- 전체 FCM 토큰 조회 → `pgmqService.send("notification_queue", event)` 로 토큰별 이벤트 발행
- 공지 저장 + 큐 발행이 같은 트랜잭션 (PGMQ는 PostgreSQL 기반 → 원자적)
- 알림 발행 실패는 흡수하여 공지 저장은 성공 유지
- 알림 발행은 `NotificationConsumerWorker` 가 400개 단위로 drain (기존 파이프라인 그대로)

**공개 엔드포인트 변경**: `GET /api/*/system/configs/notices` → `GET /api/*/notices` (`PUBLIC_GET_URLS` 갱신).

### 2.3 로그 파일 (prod 전용)

- **dev**: 콘솔만 (파일 로깅 없음) — `logback-spring.xml` dev 프로파일은 변경 없음
- **prod**: `logs/${INSTANCE_ID}-application.log` (Logstash JSON) — 기존 유지
- `logging.file.name` 을 prod에 명시하여 `/actuator/logfile` 엔드포인트가 파일을 찾도록 함
- `app.instance-id: ${INSTANCE_ID:app}` 추가 → logback 파일명과 actuator 경로가 일치

> dev에서는 `/actuator/logfile` 이 404 (파일 없음). 관리자 페이지 로그 탭은 prod에서만 실제 로그를 보여줌.

---

## 3. 프론트 구현 내용

### 3.1 접근 제어

| 파일 | 내용 |
|------|------|
| `shared/utils/accessLevel.ts` | `PageAuthLevel` 에 `admin` 레벨 추가 (ADMIN → admin) |
| `router/index.ts` | `/system` 라우트에 `meta: { requiredAuth: 'admin' }` |

### 3.2 서비스

| 파일 | 내용 |
|------|------|
| `shared/services/noticeService.ts` | 조회 엔드포인트 `/system/configs/notices` → `/notices` 변경, 관리자 CRUD(`createNotice`/`updateNotice`/`deleteNotice`) 추가 |
| `shared/services/actuatorService.ts` (신규) | `/actuator/*` raw fetch 헬퍼: health, metrics, loggers(+레벨 변경), logfile tail(Range 요청). 401 시 silent refresh 1회 재시도 |

> 기존 공개 공지 페이지(`NoticePage.vue`)는 `fetchNotices()` 만 사용하므로 별도 수정 불필요 (id가 실제 서버 id로 바뀜).

### 3.3 관리자 페이지 (`features/system/ui/SystemPage.vue`) — 전면 재작성

기존 하드코딩 목업(지표/토글/문의/서버상태) 제거, 4개 탭으로 재구성:

| 탭 | 데이터 소스 | 내용 |
|----|------------|------|
| **공지 관리** | `/api/1/notices` (커스텀) | 목록/등록/수정/삭제. 등록 시 "전체 사용자에게 푸시 발송" 안내 |
| **시스템 설정** | `/api/1/system/configs` (기존 SystemConfigController) | `current_term`(형식 검증), `announcement` 배너 저장 + 전체 설정 조회 테이블 |
| **모니터링** | `/actuator/health`, `/actuator/metrics/*` | 서비스 상태 + JVM 메모리/CPU/uptime/스레드/DB 커넥션/HTTP 요청 수 |
| **로그** | `/actuator/loggers`, `/actuator/logfile` | 로그 레벨 실시간 변경(ROOT, com.mjusugangsincheonghelper) + 최근 200줄 tail 뷰어(자동 갱신 토글) |

---

## 4. Actuator 엔드포인트 사용법 (관리자 전용)

| 엔드포인트 | 용도 | 프론트 사용처 |
|------------|------|---------------|
| `GET /actuator/health` | 서비스/DB/Redis 상태 | 모니터링 탭 헤더 배지 + 상태 카드 |
| `GET /actuator/metrics/{name}` | 메트릭 조회 (jvm, cpu, hikaricp, http.server.requests 등) | 모니터링 탭 카드 |
| `GET /actuator/loggers` | 로거 목록/현재 레벨 | 로그 탭 |
| `POST /actuator/loggers/{name}` | 런타임 로그 레벨 변경 `{"configuredLevel":"DEBUG"}` | 로그 탭 select |
| `GET /actuator/logfile` | 로그 파일 tail (Range 지원, prod 전용) | 로그 탭 뷰어 |
| `GET /actuator/threaddump` | 스레드 덤프 (필요 시) | — |

---

## 5. 남은 작업 / 체크리스트

- [ ] 프론트 `SystemPage.vue` 실제 운영 반영 (dev에서 logfile 404 노출은 의도된 동작)
- [ ] `docs/system_config_architecture.md` 의 `notices` 키 언급 갱신 (전용 테이블로 이전됨)
- [ ] `docs/ddl.md` 에 notice 테이블 반영 여부 확인
- [ ] 관리자 계정 준비: dev `TEST_ADMIN` 사용, prod는 DB에서 `member.role = 'ADMIN'` 직접 지정
- [ ] 운영 반영 시 `/actuator/*` 접근 로그 확인 (보안 모니터링)

---

## 6. 테스트 현황

| 스위트 | 결과 |
|--------|------|
| 백엔드 `NoticeControllerTest` | 4개 통과 (목록/등록/수정/삭제) |
| 백엔드 `NoticeServiceTest` | 4개 통과 (broadcast, 토큰 없음, 발행 실패 무시, NOT_FOUND) |
| 백엔드 전체 테스트 | 445개 통과 |
| 프론트 전체 테스트 (vitest) | 42개 통과 |
| 프론트 타입 체크 (`vue-tsc --noEmit`) | 통과 |
