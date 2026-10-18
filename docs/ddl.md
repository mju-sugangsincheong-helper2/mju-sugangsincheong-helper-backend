# DDL 관리 전략

## 환경별 구성

| 환경 | ddl-auto | DDL 적용 주체 | SQL 파일 |
|------|----------|---------------|----------|
| **dev** | `validate` | `db-setup` 컨테이너가 psql로 적용 (Spring `sql.init.mode: never`) | `schema-dev.sql` (전체) |
| **test** | `validate` | Spring `sql.init` (`mode: always`, `schema-dev.sql`) | `schema-dev.sql` (전체, dev와 동일) |
| **prod** | `validate` | `db-setup` 컨테이너가 psql로 적용 (Spring `sql.init.mode: never`) | `schema-prod.sql` (전체) |

### 실행 순서

**dev** (`docker-compose-dev.yml` → `db-setup` → psql):
```
1. db-setup 컨테이너가 dev / test DB에 schema-dev.sql 적용 (테이블+파티션+뷰, 멱등)
2. Spring ddl-auto: validate → 스키마 일치 검증만 (자동 수정 금지)
```

**test** (`application-test.yml`, `defer-datasource-initialization: false`):
```
1. Spring sql.init이 schema-dev.sql 적용 (테이블+파티션+뷰)
2. Hibernate ddl-auto: validate → 검증만
```

**prod** (`docker-compose-prod.yml` → `db-setup` → psql):
```
1. db-setup 컨테이너가 prod DB에 schema-prod.sql 적용 (DO $$ 블록 포함 — Spring 파서가 처리 불가하므로 psql 직접 실행)
2. Spring ddl-auto: validate → 검증만
```

---

## dev/test 설정

### `application-dev.yml`

```yaml
spring:
  sql:
    init:
      mode: never  # schema-dev.sql은 db-setup 컨테이너가 psql로 적용. Spring은 실행 안 함
  jpa:
    hibernate:
      ddl-auto: validate
```

### `application-test.yml`

```yaml
spring:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-dev.sql  # dev와 동일 DDL
  jpa:
    defer-datasource-initialization: false
    hibernate:
      ddl-auto: validate
```

---

## prod 설정

### `application-prod.yml`

```yaml
spring:
  sql:
    init:
      mode: never  # schema-prod.sql은 db-setup 컨테이너에서 psql로 적용. Spring은 실행 안 함
  jpa:
    hibernate:
      ddl-auto: validate
```

---

## 스키마 파일

- `src/main/resources/schema-dev.sql` — 개발/테스트 전체 DDL. prod와 DDL 동일, 유일한 차이는 `DO $$` 블록을 Spring·psql 양쪽에서 실행 가능한 명시적 파티션 DDL로 전개한 것.
- `src/main/resources/schema-prod.sql` — 운영 전체 DDL. `course`·`exchange_*` 테이블은 학기(term) 기준 LIST 파티셔닝, 신학기 파티션은 `DO $$` 블록이 2025~2029년치 미리 생성.
- 뷰 4개(`v_sequence_percentile_stats`, `v_ranking_page`, `v_my_records_page`, `v_analysis_page`)는 dev/prod 양쪽 파일 13절에 포함. 별도 뷰 전용 파일 없음.
