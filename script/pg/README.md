# PostgreSQL 운영 조회 스크립트

## 원칙

- 기존 프로젝트의 `.env`를 그대로 사용한다.
- 추가 환경변수를 요구하지 않는다.
- `.env` 전체를 `source`하지 않는다.
  - Firebase private key 때문에 `.env`를 shell script로 실행하면 안 된다.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`만 안전하게 읽는다.
- Docker Compose 파일 경로를 요구하지 않는다.
- 컨테이너 이름을 `db`로 하드코딩하지 않는다.
- 실행 중인 Docker 컨테이너를 검사하여 `POSTGRES_USER` + `POSTGRES_DB`가 일치하는 PostgreSQL 컨테이너를 찾는다.
- 조회 스크립트는 DB 데이터를 변경하지 않는다.
- `all.sh`는 19개의 조회/진단 스크립트를 순서대로 실행한다.

## 설치

프로젝트 루트에서 이 디렉터리를 덮어쓴다.

```bash
chmod +x script/pg/*.sh
```

## 실행

```bash
bash script/pg/health.sh
bash script/pg/activity.sh
bash script/pg/locks.sh
bash script/pg/long-transactions.sh
bash script/pg/dead-tuples.sh
bash script/pg/vacuum.sh
bash script/pg/bloat.sh
bash script/pg/xid-age.sh
bash script/pg/connections.sh
bash script/pg/slow-queries.sh
bash script/pg/query-stats.sh
bash script/pg/tables.sh
bash script/pg/indexes.sh
bash script/pg/unused-indexes.sh
bash script/pg/queue-pgmq.sh
bash script/pg/queue-health.sh
bash script/pg/cron.sh
bash script/pg/replication.sh
bash script/pg/settings.sh
```

전체:

```bash
bash script/pg/all.sh
```

## 중요

`DB_URL`의 `db`는 PostgreSQL 접속용 hostname/service name일 뿐이다.
이 스크립트는 `docker exec db ...`를 하지 않는다.

실제 실행 중인 PostgreSQL 컨테이너를 Docker metadata에서 찾아
그 컨테이너에 `psql`을 실행한다.

따라서 Compose가 다음처럼 컨테이너 이름을 생성하더라도 상관없다.

- `db`
- `project-db-1`
- `mju-sugangsincheong-helper-db-1`
- 기타 Compose-generated name

단, PostgreSQL 컨테이너가 실행 중이어야 하며 해당 컨테이너에 `psql`이 있어야 한다.

## 조회 전용

이 묶음에는 `VACUUM`, `ANALYZE`, `REINDEX`, `DROP`, `DELETE`, `UPDATE`, `ALTER` 등의 변경 명령을 넣지 않았다.

`vacuum.sh`도 VACUUM을 실행하지 않고 현재 상태만 조회한다.
