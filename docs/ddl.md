# DDL 관리 전략

## 환경별 구성

| 환경 | ddl-auto | SQL init 파일 | defer |
|------|----------|---------------|-------|
| **dev** | `create-drop` | `schema-view.sql` (뷰만) | `true` |
| **test** | `create-drop` | `schema-view.sql` (뷰만) | `true` |
| **prod** | `validate` | `schema-prod.sql` (전체) | `true` |

### 실행 순서

**dev/test** (`defer-datasource-initialization: true`):
```
1. Hibernate ddl-auto: create-drop  → 모든 일반 테이블 생성 (course는 일반 테이블)
2. schema-view.sql                  → 뷰만 생성 (CREATE OR REPLACE VIEW)
```

**prod** (`defer-datasource-initialization: true`):
```
1. Hibernate ddl-auto: validate     → 엔티티와 테이블 일치 검증만
2. schema-prod.sql                  → course 파티셔닝 테이블 + 모든 뷰 생성
```

---

## dev/test 설정

### `application-dev.yml` / `application-test.yml`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-view.sql
  jpa:
    defer-datasource-initialization: true
```

### `src/main/resources/schema-view.sql`

```sql
CREATE OR REPLACE VIEW v_sequence_percentile_stats AS ... ;
CREATE OR REPLACE VIEW v_ranking_page AS ... ;
CREATE OR REPLACE VIEW v_my_records_page AS ... ;
CREATE OR REPLACE VIEW v_analysis_page AS ... ;
```

> `course`는 Hibernate가 일반 테이블로 생성하므로 별도 DDL 불필요.

---

## prod 설정

### `application-prod.yml`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-prod.sql
  jpa:
    defer-datasource-initialization: true
```

### `src/main/resources/schema-prod.sql`

#### 1. course (파티셔닝)

```sql
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

-- 파티션은 매 학기 수동 추가
-- CREATE TABLE course_202510 PARTITION OF course FOR VALUES IN ('202510');
```

##### 파티션 자동 생성 트리거 (선택)

트리거를 사용하면 신규 term INSERT 시 자동으로 파티션이 생성됩니다.

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

#### 2. single_game

```sql
CREATE TABLE IF NOT EXISTS single_game (
    id            SERIAL       PRIMARY KEY,
    member_id     INT          NOT NULL REFERENCES member(id),
    t_total       INT          NOT NULL,
    t_enter_main  INT          NOT NULL,
    is_completed  BOOLEAN      NOT NULL,
    total_courses INT          NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_game_ranking ON single_game (total_courses, is_completed, t_total);
CREATE INDEX idx_game_member ON single_game (member_id, created_at DESC);

CREATE TABLE IF NOT EXISTS single_game_detail (
    game_id        INT  NOT NULL REFERENCES single_game(id) ON DELETE CASCADE,
    sequence       INT  NOT NULL,
    t_click_course INT  NOT NULL,
    t_click_yes    INT  NOT NULL,
    t_click_ok     INT  NOT NULL,
    PRIMARY KEY (game_id, sequence)
);
```

#### 3. 뷰 (4개)

```sql
-- 백분위 경계값 통계 (5선 차트용)
CREATE OR REPLACE VIEW v_sequence_percentile_stats AS
WITH ranked AS (
    SELECT
        sg.total_courses,
        d.sequence,
        d.t_click_course,
        d.t_click_yes,
        d.t_click_ok,
        (d.t_click_course + d.t_click_yes + d.t_click_ok) AS total,
        ROW_NUMBER() OVER (
            PARTITION BY sg.total_courses, d.sequence
            ORDER BY (d.t_click_course + d.t_click_yes + d.t_click_ok) ASC
        ) AS rn,
        COUNT(*) OVER (PARTITION BY sg.total_courses, d.sequence) AS cnt
    FROM single_game sg
    JOIN single_game_detail d ON sg.id = d.game_id
    WHERE sg.is_completed = TRUE
)
SELECT
    total_courses,
    sequence,
    PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY t_click_course) AS p10_click_course,
    PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY t_click_course) AS p30_click_course,
    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY t_click_course) AS p50_click_course,
    PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY t_click_course) AS p70_click_course,
    PERCENTILE_CONT(1.00) WITHIN GROUP (ORDER BY t_click_course) AS p100_click_course,
    PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY t_click_yes)    AS p10_click_yes,
    PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY t_click_yes)    AS p30_click_yes,
    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY t_click_yes)    AS p50_click_yes,
    PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY t_click_yes)    AS p70_click_yes,
    PERCENTILE_CONT(1.00) WITHIN GROUP (ORDER BY t_click_yes)    AS p100_click_yes,
    PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY t_click_ok)     AS p10_click_ok,
    PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY t_click_ok)     AS p30_click_ok,
    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY t_click_ok)     AS p50_click_ok,
    PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY t_click_ok)     AS p70_click_ok,
    PERCENTILE_CONT(1.00) WITHIN GROUP (ORDER BY t_click_ok)     AS p100_click_ok,
    PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY total)          AS p10_total,
    PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY total)          AS p30_total,
    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY total)          AS p50_total,
    PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY total)          AS p70_total,
    PERCENTILE_CONT(1.00) WITHIN GROUP (ORDER BY total)          AS p100_total
FROM ranked
GROUP BY total_courses, sequence;

-- 랭킹 페이지 전용 뷰
CREATE OR REPLACE VIEW v_ranking_page AS
WITH first_click AS (
    SELECT game_id, t_click_course
    FROM single_game_detail
    WHERE sequence = 1
)
SELECT
    sg.id AS game_id,
    sg.member_id,
    m.name AS member_name,
    m.department,
    sg.total_courses,
    sg.t_total,
    sg.t_enter_main,
    fc.t_click_course AS t_click_course_1st,
    sg.created_at,
    ROW_NUMBER() OVER (PARTITION BY sg.total_courses ORDER BY sg.t_total ASC, sg.created_at ASC) AS global_rank,
    ROW_NUMBER() OVER (PARTITION BY sg.total_courses, m.department ORDER BY sg.t_total ASC, sg.created_at ASC) AS dept_rank,
    ROW_NUMBER() OVER (PARTITION BY sg.total_courses ORDER BY sg.t_enter_main ASC, sg.created_at ASC) AS enter_main_rank,
    ROW_NUMBER() OVER (PARTITION BY sg.total_courses ORDER BY fc.t_click_course ASC, sg.created_at ASC) AS first_click_rank
FROM single_game sg
JOIN member m ON sg.member_id = m.id
LEFT JOIN first_click fc ON sg.id = fc.game_id
WHERE sg.is_completed = TRUE;

-- 내 기록 페이지 전용 뷰
CREATE OR REPLACE VIEW v_my_records_page AS
SELECT
    rp.game_id,
    rp.member_id,
    rp.total_courses,
    rp.t_total,
    rp.t_enter_main,
    rp.created_at,
    rp.global_rank,
    rp.dept_rank,
    (SELECT COUNT(*) FROM single_game WHERE total_courses = rp.total_courses AND is_completed = TRUE) AS total_global_players,
    (SELECT COUNT(*) FROM single_game sg JOIN member m ON sg.member_id = m.id WHERE sg.total_courses = rp.total_courses AND m.department = rp.department AND sg.is_completed = TRUE) AS total_dept_players
FROM v_ranking_page rp;

-- 분석 페이지 전용 뷰
CREATE OR REPLACE VIEW v_analysis_page AS
SELECT
    sgd.game_id,
    sgd.sequence,
    sgd.t_click_course AS mine_click_course,
    sgd.t_click_yes AS mine_click_yes,
    sgd.t_click_ok AS mine_click_ok,
    (sgd.t_click_course + sgd.t_click_yes + sgd.t_click_ok) AS mine_total,
    vps.p10_click_course, vps.p30_click_course, vps.p50_click_course, vps.p70_click_course, vps.p100_click_course,
    vps.p10_click_yes,    vps.p30_click_yes,    vps.p50_click_yes,    vps.p70_click_yes,    vps.p100_click_yes,
    vps.p10_click_ok,     vps.p30_click_ok,     vps.p50_click_ok,     vps.p70_click_ok,     vps.p100_click_ok,
    vps.p10_total,        vps.p30_total,        vps.p50_total,        vps.p70_total,        vps.p100_total
FROM single_game_detail sgd
JOIN single_game sg ON sgd.game_id = sg.id
LEFT JOIN v_sequence_percentile_stats vps
    ON vps.total_courses = sg.total_courses AND vps.sequence = sgd.sequence;
```
