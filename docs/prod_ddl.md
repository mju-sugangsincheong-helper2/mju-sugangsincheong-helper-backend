# Production DDL 관리

## 개요

`course` 테이블은 PostgreSQL 선언적 파티셔닝을 사용하므로 JPA `ddl-auto`로 생성할 수 없습니다.
운영 환경에서는 `init-ddl.sql`로 직접 DDL을 관리합니다.

---

## 설정

### 1. `src/main/resources/init-ddl.sql` 생성

```sql
-- 운영 환경에서만 사용. dev/test는 Hibernate ddl-auto로 자동 생성.

CREATE TABLE IF NOT EXISTS course (
    coursecls   VARCHAR(10) NOT NULL,
    term        VARCHAR(6)  NOT NULL,
    campusdiv   VARCHAR(10),
    classdiv    VARCHAR(10),
    gbn         VARCHAR(10),
    curigbn     VARCHAR(10),
    comyear     VARCHAR(10),
    curinum     VARCHAR(50),
    curinum2    VARCHAR(50),
    curinm      VARCHAR(200),
    groupcd     VARCHAR(50),
    cdtnum      VARCHAR(10),
    cdttime     VARCHAR(10),
    takelim     VARCHAR(10),
    listennow   VARCHAR(10),
    deptcd      VARCHAR(50),
    deptnm      VARCHAR(100),
    profid      VARCHAR(50),
    profnm      VARCHAR(100),
    largetp     VARCHAR(10),
    smalltp     VARCHAR(10),
    abotp       VARCHAR(10),
    lecttime    TEXT,
    dislevel    VARCHAR(10),
    curicontent TEXT,
    bagcnt      VARCHAR(10),
    dbtimelist  TEXT,
    sugyn       VARCHAR(10),
    addtime     VARCHAR(50),
    internetyn  VARCHAR(10),
    flexyn      VARCHAR(10),
    classtype   VARCHAR(10),
    lecperiod   TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (coursecls, term)
) PARTITION BY LIST (term);

-- 신규 term 파티션은 수동 추가
-- CREATE TABLE course_202510 PARTITION OF course FOR VALUES IN ('202510');
-- CREATE TABLE course_202515 PARTITION OF course FOR VALUES IN ('202515');
-- CREATE TABLE course_202520 PARTITION OF course FOR VALUES IN ('202520');
-- CREATE TABLE course_202525 PARTITION OF course FOR VALUES IN ('202525');
```

### 2. `application-prod.yml` 변경

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none          # validate → none (SQL 파일로 직접 관리)
  sql:
    init:
      mode: always
      schema-locations: classpath:init-ddl.sql
```

---

## 파티션 자동 생성

### 방법 1: PostgreSQL 트리거 (추천)

`init-ddl.sql`에 아래 트리거를 포함하면 새 `term` 값 INSERT 시 자동으로 파티션이 생성됩니다.

```sql
CREATE OR REPLACE FUNCTION auto_create_partition()
RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_class WHERE relname = 'course_' || NEW.term
    ) THEN
        EXECUTE 'CREATE TABLE course_' || NEW.term ||
                ' PARTITION OF course FOR VALUES IN (' || quote_literal(NEW.term) || ')';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auto_create_partition
    BEFORE INSERT ON course
    FOR EACH ROW EXECUTE FUNCTION auto_create_partition();
```

동작 방식:
1. 처음 보는 `term`(예: `"202610"`)이 INSERT 됨
2. 트리거가 `course_202610` 파티션 존재 여부 확인
3. 없으면 `CREATE TABLE course_202610 PARTITION OF course FOR VALUES IN ('202610')` 실행
4. INSERT 정상 진행

`init-ddl.sql`에서 `CREATE TABLE ... PARTITION BY LIST (term)` 다음에 위치시키면 됩니다.

## 수동 생성 (트리거 미사용 시)

트리거 없이 사용하려면 매 학기마다 직접 추가합니다.

```sql
CREATE TABLE course_202610 PARTITION OF course FOR VALUES IN ('202610');
```

### 다른 테이블은 Hibernate ddl-auto 가 필요할 수 있음

`course` 외 다른 엔티티는 Hibernate `ddl-auto: validate`로 검증이 필요하다면,
`init-ddl.sql`에서 `course` 테이블만 `CREATE TABLE IF NOT EXISTS`로 생성하고
`course` 관련 설정을 `spring.jpa.properties`로 분리할 수 있습니다:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate       # 다른 엔티티 검증 유지
  sql:
    init:
      mode: always
      schema-locations: classpath:init-ddl.sql
      continue-on-error: true  # course table only, 이미 존재하면 무시
```

이 경우 `init-ddl.sql` 에서 `course` 외 다른 테이블을 생성하지 않도록 주의합니다.

---

## dev/test 환경

개발/테스트 환경은 Hibernate `ddl-auto`로 일반 테이블을 생성합니다.
파티셔닝 없이 `term` 컬럼에 인덱스만 있습니다.

```sql
CREATE INDEX ON course (term);
```
