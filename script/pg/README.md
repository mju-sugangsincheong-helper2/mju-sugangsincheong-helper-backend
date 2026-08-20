# script/pg

PostgreSQL 운영 상태 **조회 전용** 스크립트 모음.

## 중요

- 프로젝트 루트의 기존 `.env`를 그대로 사용한다.
- `.env`를 `source`하지 않는다.
- 추가 환경변수를 요구하지 않는다.
- `docker compose`를 사용하지 않는다.
- `COMPOSE_FILE`을 사용하지 않는다.
- PostgreSQL 컨테이너 이름을 `db`로 가정하지 않는다.
- 실행 중인 Docker 컨테이너의 `POSTGRES_USER` / `POSTGRES_DB`를 검사해서 PostgreSQL 컨테이너를 찾는다.
- DB 변경 명령은 실행하지 않는다.

## 실행

```bash
chmod +x script/pg/*.sh
bash script/pg/health.sh
bash script/pg/all.sh
```

## 포함

health, activity, locks, long-transactions, dead-tuples, vacuum 상태,
bloat 지표, XID age, connections, slow queries, query statistics,
tables, indexes, unused indexes, queue health, pg_cron, replication,
중요 PostgreSQL settings.
