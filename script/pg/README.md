# PostgreSQL production operations

이 디렉터리는 이 프로젝트의 `docker-compose-prod.yml`과 `.env`를 전제로 한다.

## 추가 환경변수 없음

스크립트는 실행 시 자동으로:

```text
프로젝트 루트/.env
```

를 읽는다.

따라서 별도의:

```text
PG_CONTAINER=
PG_DATABASE=
PG_USER=
PG_PASSWORD=
```

같은 환경변수를 설정할 필요가 없다.

현재 프로젝트의 `.env`에서:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

를 읽고 DB 이름을 `DB_URL`에서 자동 추출한다.

PostgreSQL 컨테이너는 `docker-compose-prod.yml`에 정의된:

```text
mju-sugangsincheong-helper-db
```

를 사용한다.

## 기본 사용

```bash
./script/pg/health.sh
```

전체 운영 점검:

```bash
./script/pg/all.sh
```

## 운영 장애 시 추천 순서

```bash
./script/pg/health.sh
./script/pg/activity.sh
./script/pg/locks.sh
./script/pg/long-transactions.sh
./script/pg/dead-tuples.sh
./script/pg/vacuum.sh
./script/pg/bloat.sh
```

큐 문제가 의심되면:

```bash
./script/pg/queue-health.sh
./script/pg/queue-pgmq.sh
```

쿼리 성능:

```bash
./script/pg/slow-queries.sh
./script/pg/query-stats.sh
```

DB 자체 상태:

```bash
./script/pg/connections.sh
./script/pg/xid-age.sh
./script/pg/settings.sh
```

## Long transaction

기본 1분:

```bash
./script/pg/long-transactions.sh
```

5분:

```bash
./script/pg/long-transactions.sh "5 minutes"
```

## 안전성

현재 스크립트 모음은 **진단 전용(read-only)** 이다.

자동으로 다음을 수행하지 않는다.

- DELETE
- TRUNCATE
- VACUUM FULL
- REINDEX
- DROP INDEX
- pg_terminate_backend()
- ALTER SYSTEM
- PostgreSQL 설정 변경

운영 조치는 별도의 `maintenance/` 계층으로 분리하는 것을 권장한다.
