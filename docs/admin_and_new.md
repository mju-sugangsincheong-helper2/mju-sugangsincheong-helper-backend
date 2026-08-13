# Admin & 신규 기능 정리 (관리자 시스템 + 최근 변경 사항)

> 본 문서는 관리자(Admin) 기능과 함께, 이번에 백엔드/프론트에서 변경·추가된 사항을 정리합니다.
> 최종 목표: **프론트 관리자 페이지(관리자 시스템)는 공지 관리만 커스텀 API를 쓰고, 나머지(모니터링/로그)는 Actuator를 최대한 그대로 활용한다.**

---

## 1. 개요

| 항목 | 결정 사항 |
|------|-----------|
| 공지 저장 | `system_config.notices` (JSON) → **전용 `notice` 테이블로 이전** |
| 공지 등록 시 알림 | 기존 PGMQ → FCM 파이프라인 그대로 사용 (전체 사용자 broadcast) |
| 모니터링 | **도메인 지표 API** (`GET /api/{v}/system/stats`) + 인프라 지표는 **VictoriaMetrics 단독 수집** (`/actuator/prometheus` 스크레이핑) |
| 로그 | **내부망 전용 standalone 페이지** (`internal_system/index.html`)가 Spring Boot Actuator 활용 (loggers, logfile) |
| 도메인 기능 플래그 | ❌ 구현 안 함 |
| 멤버 관리 | ❌ DB 직접 접속으로 처리 |
| 문의/제보 관리 | ❌ 카카오톡 오픈채팅으로 처리 |
| 로그 파일 | **dev/prod 모두 파일 로깅** (관리자 로그 뷰 지원) |

---

## 2. 백엔드 변경 사항

### 2.1 Actuator 보안 강화 (중요)

**변경 전 문제**: `/actuator/**`가 `PUBLIC_URLS`에 등록되어 있어 **인증 없이** `env`, `heapdump` 등 전체 엔드포인트 접근 가능 + `exposure: "*"`로 전부 노출.

**변경 후 (단순화: actuator 전체 공개)**:

| 항목 | 변경 내용 |
|------|-----------|
| 노출 엔드포인트 | `"*"` **전체 노출** — 백엔드가 위치한 네트워크는 내부망이고 공개 프록시가 `/api`만 라우팅하므로 env/heapdump 포함 모두 노출해도 무방 |
| 접근 제어 | `/actuator/**` **전체 공개** — 공개 프록시가 `/api` 경로만 백엔드로 라우팅하므로 actuator는 도커 네트워크/내부망에서만 접근 가능 |
| health 상세 | `show-details: always` (전체 공개 — 내부망 전제) |

**수정 파일**:

| 파일 | 내용 |
|------|------|
| `global/security/GlobalSecurityConfig.java` | `/actuator/**` 를 `PUBLIC_URLS`에 추가 (JWT 불필요). secured 체인(`/api/**`)에서 actuator matcher 제거 |
| `global/security/filter/JwtAuthenticationFilter.java` | `/actuator/` JWT 처리 제거 (공개 체인이라 필터 미적용) |
| `global/security/filter/ConsentCheckFilter.java` | `/actuator/` 동의 예외 제거 (불필요) |
| `application-dev.yml`, `application-prod.yml` | `management.endpoints.web.exposure.include` 축소 + health 상세 설정 |

> **보안 경계 = 공개 프록시 + 내부망**: `/api`만 라우팅하므로 actuator는 외부에서 도달할 수 없다. docker-compose의 `victoria-metrics` 컨테이너가 `/actuator/prometheus` 를 스크레이핑한다.

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

**등록 시 푸시 규약** (기존 `firebase_cloud_messaging_registration_token_strategy.md` 의 producer 규약 그대로):

```json
{
  "token": "<firebase_cloud_messaging_registration_token>",
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

- 전체 Firebase Cloud Messaging 토큰 조회 → `pgmqService.send("notification_queue", event)` 로 토큰별 이벤트 발행
- 공지 저장 + 큐 발행이 같은 트랜잭션 (PGMQ는 PostgreSQL 기반 → 원자적)
- 알림 발행 실패는 흡수하여 공지 저장은 성공 유지
- 알림 발행은 `NotificationConsumerWorker` 가 400개 단위로 drain (기존 파이프라인 그대로)

**공개 엔드포인트 변경**: `GET /api/*/system/configs/notices` → `GET /api/*/notices` (`PUBLIC_GET_URLS` 갱신).

### 2.3 로그 파일 (dev/prod 공통)

- **dev**: 콘솔(사람이 읽기 편한 포맷) + `logs/${INSTANCE_ID}-application.log` (JSON) 동시 기록
- **prod**: `logs/${INSTANCE_ID}-application.log` (JSON) — 기존 유지
- `logging.file.name` 을 dev/prod 모두 명시 → `/actuator/logfile` 엔드포인트가 파일을 찾음
- `app.instance-id: ${INSTANCE_ID:app}` → logback 파일명과 actuator 경로가 일치

> 파일 로그는 항상 Logstash JSON 포맷이라 프론트 뷰어에서 라인 단위 파싱이 가능. 콘솔은 dev에서만 읽기 편한 포맷 유지.

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
| `vite.config.ts` | dev 프록시에 `/actuator` 추가 (기존 `/api`처럼 백엔드로 포워딩). 미설정 시 요청이 Vite dev 서버에 가서 `index.html`이 반환됨 |

> 기존 공개 공지 페이지(`NoticePage.vue`)는 `fetchNotices()` 만 사용하므로 별도 수정 불필요 (id가 실제 서버 id로 바뀜).

### 3.3 관리자 페이지 (`features/system/ui/SystemPage.vue`) — 전면 재작성

기존 하드코딩 목업(지표/토글/문의/서버상태) 제거. **단일 페이지 + 탭 4개** 구조로 재구성.
(4개 라우트로 분리하는 것은 검토 결과 과함 — 시스템 설정이 1개 필드뿐이라 단일 페이지 + 탭이 응집도/단순성에 더 맞음)

| 파일 | 역할 |
|------|------|
| `SystemPage.vue` | 셸: 헤더 + 탭 내비게이션 + 토스트. 공지/설정/모니터링 3탭 (로그는 `internal_system/index.html` standalone으로 이전) |
| `NoticeAdminPage.vue` | 공지 탭 컴포넌트 (CRUD 폼 + 목록 — 유일하게 성격이 다른 콘텐츠 관리라 별도 컴포넌트로 분리) |
| `DomainStatsTab.vue` | 모니터링 탭 컴포넌트 (도메인 지표: 회원 구성/기기 환경/교환·게임 활성도/학기별 강좌) |
| `shared/composables/useToast.ts` | 관리자 페이지 공용 토스트 (모듈 싱글턴) |

| 탭 | 데이터 소스 | 내용 |
|----|------------|------|
| **공지 관리** | `/api/1/notices` (커스텀) | 목록/등록/수정/삭제. 등록 시 `broadcast` 옵션으로 **푸시 발송 동반 여부 선택** (FCM 발송+등록 vs 등록만), 수정은 푸시 없이 저장만 |
| **시스템 설정** | `/api/1/system/configs`, `/api/1/course/sections` | `current_term` 편집 (형식 검증) + **모든 설정 변수 조회 테이블** (system_config 전체 목록) + **강좌 JSON 붙여넣기 등록** (서버 응답/오류 표시) |
| **모니터링** | `/api/1/system/stats` (커스텀 도메인 지표) | 도메인 지표 시각화 (아래 참고) + **운영 작업**(만료 Firebase Cloud Messaging 토큰 정리 버튼) + **푸시 백로그**(PGMQ 대기 건수). 인프라 지표는 **UI에서 제거**하고 VictoriaMetrics(vmui)로 조회 |

> **로그 탭은 프론트 관리자에서 제거.** → 내부망 전용 standalone 페이지 `internal_system/index.html`이 Actuator(health/metrics/prometheus/loggers/logfile/threaddump/env/mappings/beans/conditions/caches/httpexchanges)를 전부 대시보드로 제공한다. 라이브러리는 전부 CDN(Tailwind, Chart.js).

#### 로그 "더보기" + 자동 갱신 동작 원리

> 목표: 최대 줄 수 제한 없이 서버에서 필요한 만큼만 가져온다. 바이트 계산/Content-Range 파싱/디듀프 알고리즘 없이 `indexOf` 하나로 구현.

1. **첫 로드/새로고침**: `Range: bytes=-200000` (파일 끝 200KB) 요청 → 가장 최신 로그만 표시
2. **이전 로그 더보기**: 200KB → 400KB → 800KB … 로 **꼬리 크기를 2배씩** 늘려 재요청
   - 응답은 항상 파일 끝에 고정(접미사)이라 이전 응답을 포함 → `text.indexOf(현재 버퍼)` 로 겹침 위치를 찾아 **앞의 오래된 부분만 prepend**
   - suffix 크기가 파일 크기를 넘으면 indexOf가 0을 반환 → "파일 전체 표시 중"으로 **자연 종료** (임의 최대치 없음)
   - Range 시작이 줄 중간일 수 있어 첫 줄(부분 프래그먼트)은 첫 `\n` 기준으로 잘라냄
3. **자동 갱신**: `setInterval(refreshTail, 5000)` — 최신 꼬리로 뷰를 초기화하고 아래로 스크롤 (더보기로 확장한 상태를 유지하지 않는 단순 정책)
4. 로그 레벨 변경은 별개로 `POST /actuator/loggers/{name}` — 다음 로그부터 즉시 반영 (재시작 불필요)

> 참고: dev/prod 모두 파일 로깅을 켜놓았으므로 `Range: bytes=-N`(접미사)를 지원하는 Spring Boot logfile 엔드포인트로 어느 환경에서나 동작.

---

## 4. 도메인 지표 API (모니터링 탭)

`GET /api/{version}/system/stats` (ADMIN) — 프론트 모니터링 탭의 유일한 데이터 소스. 인프라 지표는 포함하지 않는다.

| 필드 | 설명 |
|------|------|
| `members` | 회원 구성: `total` / `guest` / `regular`(정회원) / `admin` |
| `newMembersToday` / `newMembersThisWeek` | 신규 가입 (Asia/Seoul 0시 기준, 최근 7일) |
| `devices` / `activeDevicesLast7Days` | 전체 기기 / 최근 7일 접속 활성 기기 |
| `notices` / `courseSections` / `terms` | 공지 수 / 강좌 섹션 수 / 강좌가 있는 학기 수 |
| `coursesByTerm` | 학기별 강좌 수 (`[{term, count}]`) |
| `devicesByOs` / `devicesByBrowser` | OS·브라우저별 기기 분포 (`[{label, count}]`) |
| `exchange` | 현재 학기 교환 의도(`intents`) / 활성 방(`activeRooms`) / 메시지(`messages`) |
| `games` | 싱글게임 완주(`singleGameCompleted`) / 참여 멀티게임 라운드(`multigameRounds`) |
| `notificationQueueLength` | PGMQ `notification_queue` 대기(푸시 백로그) 건수 |

> **운영 작업 엔드포인트**: `POST /api/{version}/system/devices/cleanup` (ADMIN) → 만료된 기기 세션(`expiresAt < now`, 바인딩 Firebase Cloud Messaging 토큰 포함) 일괄 삭제 후 `{cleared: n}` 반환. `system/controller/SystemMaintenanceController`.

**신규 파일**: `system/controller/SystemStatsController` + `system/service/SystemStatsService` + `system/dto/SystemStatsResponse` + `system/controller/SystemMaintenanceController`

#### 모니터링 탭 시각화 (Chart.js)

- 시계열(폴링 라인)은 **하지 않음** — 현재 시점 스냅샷만 적절히 시각화 (15초 자동 갱신 + 수동 새로고침)
- **회원 구성 도넛**: 정회원/게스트/관리자 비율
- **OS 도넛 + 브라우저 가로 바**: 기기 환경 분포 (상위 N개 + 기타 합산)
- **카드 그리드**: 회원/신규 가입/기기/활성 기기/공지/강좌/학기/푸시 백로그
- **교환·게임 활성도 박스**: 교환 의도/활성 방/메시지, 싱글게임 완주/멀티 라운드
- **학기별 강좌 가로 바**

---

## 4.1 Actuator 엔드포인트 사용법

> **접근 제어**: `/actuator/**` 공개 (공개 프록시가 `/api`만 라우팅 — 내부망 전제). 접근은 내부망 전용 `internal_system/index.html` (Actuator 전체 대시보드)에서만 수행한다.

| 엔드포인트 | 용도 | 프론트 사용처 |
|------------|------|---------------|
| `GET /actuator/loggers` | 로거 목록/현재 레벨 | internal 페이지 (로거 탭) |
| `POST /actuator/loggers/{name}` | 런타임 로그 레벨 변경 `{"configuredLevel":"DEBUG"}` | internal 페이지 (로거 탭 select) |
| `GET /actuator/logfile` | 로그 파일 꼬리 조회 (Range 접미사 지원, dev/prod 공통) | internal 페이지 (로그 파일 탭) |
| `GET /actuator/prometheus` | Prometheus 포맷 지표 (JWT 불필요) | **VictoriaMetrics 스크레이핑** |
| `GET /actuator/health` | 서비스/DB/Redis 상태 (ADMIN 상세) | 외부 헬스체크/VM |
| `GET /actuator/metrics/**` 등 | 기타 읽기 전용 (threaddump/scheduledtasks/mappings/conditions/beans/caches/info) | VM(vmui) 수동 조회 |

> 노출 범위(dev/prod 동일): `"*"` 전체 (env/configprops/heapdump 포함) + health 상세 `always` — 백엔드 네트워크는 내부망이며 공개 프록시가 `/api`만 라우팅하므로 안전.

---

## 4.2 인프라 모니터링 스택 (VictoriaMetrics 단독, docker)

**선택 배경**: Prometheus + Grafana는 자체호스팅으로 너무 무거워 **VictoriaMetrics 1컨테이너**로 결정 (~50-100MB RAM, PromQL 호환, 내장 `vmui` UI).

```
docker compose up -d victoria-metrics   # http://localhost:8428 (vmui)
```

| 항목 | 내용 |
|------|------|
| 스크레이프 대상 | `host.docker.internal:8080` (백엔드가 호스트 실행 중) — 앱 컨테이너화 시 `app:8080`으로 변경 |
| 스크레이프 주기 | 15s (`docker/victoriametrics/prometheus.yml`) |
| 보존 기간 | 30일 (`-retentionPeriod=30d`) |
| UI | vmui (http://localhost:8428) — PromQL 쿼리/차트/얼럿 |

- `docker-compose.yml` (dev): `victoria-metrics` 서비스 + `victoriametrics_data` 볼륨 추가
- `docker-compose-prod.yml` (신규, dev 기반): `db` + `db-setup` + `redis` + `victoria-metrics` + `pgweb`(내부망 전용, SSH 터널) — dev 전용 도구(pgadmin/redis-commander) 제외, 앱 서비스는 Dockerfile 준비 후 주석 해제

---

## 5. 남은 작업 / 체크리스트

- [ ] dev에서 `/actuator/*` 가 동작하려면 **Vite dev 서버 재시작 필요** (프록시 설정 변경 반영)
- [ ] dev 로그 뷰는 **백엔드 재시작 필요** (logback dev 프로파일에 파일 appender 추가 반영)
- [ ] 프론트 `SystemPage.vue` 실제 운영 반영
- [x] `docs/system_config_architecture.md` 의 `notices` 키 언급 갱신 (전용 테이블로 이전됨)
- [ ] `docs/ddl.md` 에 notice 테이블 반영 여부 확인
- [ ] 관리자 계정 준비: dev `TEST_ADMIN` 사용, prod는 DB에서 `member.role = 'ADMIN'` 직접 지정
- [ ] 앱 서비스 컨테이너화 시 `docker/victoriametrics/prometheus.yml` 스크레이프 타깃을 `app:8080`으로 변경
- [ ] 공개 프록시가 `/api` 경로만 백엔드로 라우팅하는지 확인 (actuator는 내부망 전제)

---

## 6. 테스트 현황

| 스위트 | 결과 |
|--------|------|
| 백엔드 `NoticeControllerTest` | 4개 통과 (목록/등록/수정/삭제) |
| 백엔드 `NoticeServiceTest` | 4개 통과 (broadcast, 토큰 없음, 발행 실패 무시, NOT_FOUND) |
| 백엔드 `SystemStatsControllerTest` / `SystemStatsServiceTest` | 1 + 2개 통과 (도메인 지표 API/집계) |
| 백엔드 전체 테스트 | 448개 통과 |
| 프론트 전체 테스트 (vitest) | 42개 통과 |
| 프론트 타입 체크 (`vue-tsc --noEmit`) | 통과 |
