# PostgreSQL 운영 조회 스크립트

이 디렉터리는 **read-only 운영 진단**을 목적으로 한다.

## 중요한 원칙

- 프로젝트 루트의 `.env`를 `source`하지 않는다.
- `.env`에서 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`만 텍스트로 읽는다.
- Firebase private key 등 다른 환경변수는 읽지 않는다.
- 기본적으로 SQL `SELECT` 조회만 수행한다.
- `VACUUM`, `REINDEX`, `DELETE`, `UPDATE`, `pg_terminate_backend()` 등 변경 작업은 포함하지 않는다.
- `all.sh`는 한 진단이 실패해도 나머지 진단을 계속 실행한다.

## 실행

프로젝트 루트에서:

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

## 주요 진단 흐름

```text
activity
   │
   ├── long transaction
   │       │
   │       └── MVCC horizon
   │
   ├── locks / blocking
   │
   └── slow queries
           │
           ↓
      dead tuples
           │
           ↓
        VACUUM
           │
           ├── 정상적으로 감소
           │
           └── 계속 누적
                  │
                  ↓
                bloat
```

큐 환경에서는 추가로:

```text
PGMQ
 ├── queue state
 ├── queue-related activity
 └── cron jobs
```

를 함께 확인한다.

## DB 연결

현재 프로젝트의 `.env` 형식:

```text
DB_URL=jdbc:postgresql://db:5432/mjusugangsincheonghelperdb_prod
DB_USERNAME=...
DB_PASSWORD=...
```

에서 자동으로:

```text
container = mju-sugangsincheong-helper-db
database  = mjusugangsincheonghelperdb_prod
user      = DB_USERNAME
password  = DB_PASSWORD
```

를 결정한다.

추가 환경변수는 필요하지 않다.
