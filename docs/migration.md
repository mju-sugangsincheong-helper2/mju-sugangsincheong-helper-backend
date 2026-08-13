# FID 기반 인증 아키텍처 마이그레이션 가이드

## 개요

FID(Firebase Installation ID) 기반의 v3 인증 아키텍처로 마이그레이션합니다.

**주요 변경 사항:**
- 기기 식별: UA 기반 → `firebaseInstallationId` 기반
- 토큰 네이밍: 명확한 접두사 체계 도입
- DB 스키마: `firebase_cloud_messaging_registration_token` → `firebase_cloud_messaging_registration_token`, `firebase_installation_id` 추가

## 네이밍 체계

### 토큰 및 식별자 이름 변경

| 기존 이름 | 새로운 이름 | 설명 |
|-----------|-------------|------|
| `firebaseCloudMessagingRegistrationToken` | `firebaseCloudMessagingRegistrationToken` | Firebase Cloud Messaging 토큰 |
| `sessionAccessToken` | `sessionAccessToken` | 세션 액세스 토큰 |
| `sessionRefreshToken` | `sessionRefreshToken` | 세션 리프레시 토큰 |
| `fid` | `firebaseInstallationId` | Firebase Installation ID |

### 쿠키 이름

| 쿠키 이름 | 비고 |
|-----------|------|
| `access_token` | 유지 (기존과 동일) |
| `refresh_token` | 유지 (기존과 동일) |

### 로컬 스토리지 키 변경

| 기존 키 | 새로운 키 |
|---------|-----------|
| `mju_firebase_cloud_messaging_registration_token_cache` | `mju_firebase_cloud_messaging_registration_token_cache` |

### 파일명 변경

| 기존 파일명 | 새로운 파일명 |
|-------------|---------------|
| `fcmNotificationService.ts` | `firebaseCloudMessagingNotificationService.ts` |

---

## 1. PROD DB 마이그레이션

### 1.1 사전 준비

**접속 방법:**
```bash
# 방법 1: pgweb (웹 UI)
# http://localhost:10023 접속 → SQL 탭

# 방법 2: psql (명령줄)
docker exec -it mju-sugangsincheong-helper-db psql -U mjusugangsincheonghelperuser -d mjusugangsincheonghelperdb_prod
```

### 1.2 스키마 변경 SQL

```sql
-- Step 1: firebase_installation_id 컬럼 추가 (NULL 허용)
ALTER TABLE member_device ADD COLUMN IF NOT EXISTS firebase_installation_id VARCHAR(255);

-- Step 2: fcm_token 컬럼 이름 변경
ALTER TABLE member_device RENAME COLUMN fcm_token TO firebase_cloud_messaging_registration_token;

-- Step 3: UNIQUE 부분 인덱스 추가 (NULL이 아닌 값만 UNIQUE)
CREATE UNIQUE INDEX IF NOT EXISTS idx_member_device_firebase_installation_id 
ON member_device(firebase_installation_id) 
WHERE firebase_installation_id IS NOT NULL;

-- Step 4: 확인
\d member_device
```

### 1.3 검증

```sql
-- 컬럼 확인
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'member_device' 
ORDER BY ordinal_position;

-- 인덱스 확인
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'member_device';
```

---

## 2. PROD 백엔드 배포

### 2.1 배포 명령

```bash
cd ~/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend
./run-prod.sh
```

### 2.2 검증

```bash
# 헬스체크
curl http://localhost:10020/actuator/health

# 로그 확인
docker logs -f mju-sugangsincheong-helper-backend

# DB 스키마 확인 (pgweb)
# member_device 테이블에 firebase_installation_id 컬럼이 있는지 확인
```

### 2.3 주의사항

- `ddl-auto: validate`이므로 `schema-prod.sql`과 엔티티가 정확히 일치해야 함
- 스키마 불일치 시 앱 기동 실패

---

## 3. PROD 프론트엔드 배포

### 3.1 빌드

```bash
cd ~/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-frontend
npm run build
```

### 3.2 배포

```bash
# 배포 스크립트 실행 (환경에 따라 다름)
# 예: docker compose up -d --build
```

### 3.3 검증

```bash
# 브라우저에서 접속
# 1. 게스트 로그인 시도
# 2. DB에서 member_device 테이블 확인
# 3. firebase_installation_id 컬럼에 값이 있는지 확인
```

---

## 4. 마이그레이션 후 데이터 정리 (선택적)

### 4.1 기존 데이터 현황 확인

```sql
-- firebaseInstallationId=NULL인 기기 현황
SELECT 
    member_id, 
    COUNT(*) as device_count,
    MAX(last_accessed_at) as last_access
FROM member_device
WHERE firebase_installation_id IS NULL
GROUP BY member_id
ORDER BY device_count DESC;
```

### 4.2 만료된 기기 정리

```sql
-- 만료된 firebaseInstallationId=NULL 기기 삭제
DELETE FROM member_device
WHERE firebase_installation_id IS NULL 
AND (expires_at IS NULL OR expires_at < now());
```

### 4.3 정리 후 확인

```sql
-- 여전히 NULL인 활성 기기 확인
SELECT member_id, id, platformjs_ua, last_accessed_at, expires_at
FROM member_device
WHERE firebase_installation_id IS NULL
ORDER BY last_accessed_at DESC;
```

---

## 5. 마이그레이션 시나리오

### 5.1 기존 사용자

```
[마이그레이션 전]
┌────┬───────────┬──────────────────┬─────────────────────┬───────────┐
│ id │ member_id │ refresh_token_hash│ platformjs_ua       │ firebase_cloud_messaging_registration_token │
├────┼───────────┼──────────────────┼─────────────────────┼───────────┤
│ 1  │ 100       │ abc123...        │ Chrome/150.0.0.0    │ fcm_xxx   │
│ 2  │ 100       │ def456...        │ Chrome/151.0.0.0    │ NULL      │  ← 브라우저 업데이트로 생긴 중복
└────┴───────────┴──────────────────┴─────────────────────┴───────────┘

[Phase 1 배포 직후 - 컬럼 추가/이름 변경]
┌────┬───────────┬──────────────────┬─────────────────────┬─────────────────────────────────────┬──────────────────────┐
│ id │ member_id │ refresh_token_hash│ platformjs_ua       │ firebase_cloud_messaging_           │ firebase_installation│
│    │           │                  │                     │ registration_token                  │ _id                  │
├────┼───────────┼──────────────────┼─────────────────────┼─────────────────────────────────────┼──────────────────────┤
│ 1  │ 100       │ abc123...        │ Chrome/150.0.0.0    │ fcm_xxx                             │ NULL                 │
│ 2  │ 100       │ def456...        │ Chrome/151.0.0.0    │ NULL                                │ NULL                 │
└────┴───────────┴──────────────────┴─────────────────────┴─────────────────────────────────────┴──────────────────────┘

[Phase 2+3 배포 후 - 사용자 재로그인 시 FID 발급]
┌────┬───────────┬──────────────────┬─────────────────────┬─────────────────────────────────────┬──────────────────────┐
│ id │ member_id │ refresh_token_hash│ platformjs_ua       │ firebase_cloud_messaging_           │ firebase_installation│
│    │           │                  │                     │ registration_token                  │ _id                  │
├────┼───────────┼──────────────────┼─────────────────────┼─────────────────────────────────────┼──────────────────────┤
│ 1  │ 100       │ abc123...        │ Chrome/150.0.0.0    │ fcm_xxx                             │ NULL                 │  ← 만료 예정
│ 2  │ 100       │ def456...        │ Chrome/151.0.0.0    │ NULL                                │ NULL                 │  ← 만료 예정
│ 3  │ 100       │ jkl012...        │ Chrome/151.0.0.0    │ fcm_zzz                             │ fGk8Xm2pQr5          │  ← 새 기기 (FID 기반)
└────┴───────────┴──────────────────┴─────────────────────┴─────────────────────────────────────┴──────────────────────┘
```

### 5.2 기기 식별 시나리오

| 시나리오 | firebaseInstallationId | 결과 |
|----------|------------------------|------|
| Chrome 150 → 151 업데이트 | 동일 | ✅ 같은 기기 인식 |
| macOS 업데이트 | 동일 | ✅ 같은 기기 인식 |
| 브라우저 데이터 삭제 | 새로 발급 | 새 기기 생성 (의도된 동작) |
| 시크릿 모드 → 종료 | 새로 발급 | 새 기기 생성 (의도된 동작) |
| 다른 브라우저 사용 | 다름 | 새 기기 생성 (의도된 동작) |
| 다른 기기 사용 | 다름 | 새 기기 생성 (의도된 동작) |

---

## 6. 롤백 계획

### 6.1 DB 롤백

```sql
-- Step 1: 인덱스 삭제
DROP INDEX IF EXISTS idx_member_device_firebase_installation_id;

-- Step 2: 컬럼 이름 되돌리기
ALTER TABLE member_device RENAME COLUMN firebase_cloud_messaging_registration_token TO fcm_token;

-- Step 3: firebase_installation_id 컬럼 삭제
ALTER TABLE member_device DROP COLUMN IF EXISTS firebase_installation_id;
```

### 6.2 백엔드 롤백

```bash
# 이전 버전으로 체크아웃
git checkout <이전_커밋>

# 재배포
./run-prod.sh
```

### 6.3 프론트엔드 롤백

```bash
# 이전 버전으로 체크아웃
git checkout <이전_커밋>

# 빌드 및 배포
npm run build
# 배포 스크립트 실행
```

---

## 7. 체크리스트

### 7.1 PROD 배포 전

- [ ] DEV 환경에서 모든 기능 테스트 완료
- [ ] PROD DB 백업 완료
- [ ] 롤백 계획 확인
- [ ] 배포 시간 계획 (사용자 트래픽 적은 시간대)

### 7.2 PROD 배포 중

- [ ] DB 스키마 변경 SQL 실행
- [ ] DB 스키마 검증
- [ ] 백엔드 배포
- [ ] 백엔드 헬스체크
- [ ] 프론트엔드 배포
- [ ] 프론트엔드 동작 확인

### 7.3 PROD 배포 후

- [ ] 실제 사용자 로그인 테스트
- [ ] DB에서 firebase_installation_id 확인
- [ ] 브라우저 업데이트 후 같은 기기로 인식되는지 확인
- [ ] 로그 확인 (에러 없음)
- [ ] 모니터링 (24시간)

### 7.4 배포 후 정리 (1~2주 후)

- [ ] firebaseInstallationId=NULL인 만료 기기 현황 확인
- [ ] 만료 기기 삭제 SQL 실행
- [ ] 최종 확인: 모든 활성 기기에 firebaseInstallationId 설정됨

---

## 8. 문제 해결

### 8.1 백엔드 기동 실패

**증상:** `ddl-auto: validate` 에러
**원인:** schema-prod.sql과 엔티티 불일치
**해결:** 
```bash
# 로그 확인
docker logs mju-sugangsincheong-helper-backend

# DB 스키마 확인
\d member_device

# schema-prod.sql과 비교 후 수정
```

### 8.2 기존 사용자가 로그인할 때 기기가 2개로 늘어남

**원인:** 기존 기기는 firebaseInstallationId=NULL, 새 로그인은 FID 기반
**해결:** 자연 정화 (7일 후 만료) 또는 수동 정리 (4.2절)

### 8.3 브라우저 업데이트 후 다른 기기로 인식

**원인:** v2에서는 UA 기반으로 매칭, v3에서는 FID 기반
**해결:** 정상 동작. FID는 브라우저 업데이트해도 변경되지 않음

---

## 9. 참고 문서

- [auth_architecture3.md](./auth_architecture3.md): 전체 아키텍처 문서
- [schema-prod.sql](../src/main/resources/schema-prod.sql): PROD 스키마
- [MemberDevice.java](../src/main/java/com/mjusugangsincheonghelper/database/entity/MemberDevice.java): 엔티티

---

## 10. 문의

문제 발생 시:
1. 로그 확인
2. DB 스키마 확인
3. 이 문서의 문제 해결 섹션 참고
4. auth_architecture3.md 참고
