-- 운영 환경 전체 DDL
-- 모든 테이블 + course 파티셔닝 + 분석용 뷰 4개

-- ============================================================
-- 0. 확장 기능
-- ============================================================
CREATE EXTENSION IF NOT EXISTS pgmq;

-- ============================================================
-- 1. member
-- ============================================================
CREATE TABLE IF NOT EXISTS member (
    id          SERIAL       PRIMARY KEY,
    role        VARCHAR(20)  NOT NULL,
    position    VARCHAR(50),
    department  VARCHAR(50),
    name        VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- 2. member_auth
-- ============================================================
CREATE TABLE IF NOT EXISTS member_auth (
    id            SERIAL       PRIMARY KEY,
    member_id     INT          NOT NULL UNIQUE,
    auth_type     VARCHAR(20)  NOT NULL,
    auth_key      VARCHAR(255) NOT NULL UNIQUE,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- 3. member_device
-- ============================================================
CREATE TABLE IF NOT EXISTS member_device (
    id                      SERIAL       PRIMARY KEY,
    member_id               INT          NOT NULL,
    refresh_token           VARCHAR(512) NOT NULL UNIQUE,
    fcm_token               VARCHAR(512),
    platformjs_name         VARCHAR(100),
    platformjs_version      VARCHAR(50),
    platformjs_layout       VARCHAR(50),
    platformjs_prerelease   VARCHAR(50),
    platformjs_os           VARCHAR(100),
    platformjs_manufacturer VARCHAR(100),
    platformjs_product      VARCHAR(100),
    platformjs_description  TEXT,
    platformjs_ua           TEXT,
    last_accessed_at        TIMESTAMP,
    expires_at              TIMESTAMP,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- 4. member_agreements
-- ============================================================
CREATE TABLE IF NOT EXISTS member_agreements (
    member_id   INT       PRIMARY KEY,
    status      BOOLEAN   NOT NULL DEFAULT FALSE,
    agreed_at   TIMESTAMP
);

-- ============================================================
-- 5. system_config
-- ============================================================
CREATE TABLE IF NOT EXISTS system_config (
    config_key    VARCHAR(100) PRIMARY KEY,
    config_value  TEXT         NOT NULL,
    config_type   VARCHAR(20)  NOT NULL,
    description   TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- 6. examples
-- ============================================================
CREATE TABLE IF NOT EXISTS examples (
    id          SERIAL       PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    content     TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- 7. course (파티셔닝)
-- ============================================================
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

-- 파티션 자동 생성 트리거
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

DROP TRIGGER IF EXISTS trg_auto_create_partition ON course;
CREATE TRIGGER trg_auto_create_partition
    BEFORE INSERT ON course
    FOR EACH ROW EXECUTE FUNCTION auto_create_partition();

-- ============================================================
-- 8. single_game
-- ============================================================
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

CREATE INDEX IF NOT EXISTS idx_game_ranking ON single_game (total_courses, is_completed, t_total);
CREATE INDEX IF NOT EXISTS idx_game_member ON single_game (member_id, created_at DESC);

-- ============================================================
-- 9. single_game_detail
-- ============================================================
CREATE TABLE IF NOT EXISTS single_game_detail (
    game_id        INT  NOT NULL REFERENCES single_game(id) ON DELETE CASCADE,
    sequence       INT  NOT NULL,
    t_click_course INT  NOT NULL,
    t_click_yes    INT  NOT NULL,
    t_click_ok     INT  NOT NULL,
    PRIMARY KEY (game_id, sequence)
);

-- ============================================================
-- 10. 뷰
-- ============================================================
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

-- ============================================================
-- 11. exchange_intent (파티셔닝)
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_intent (
    term            VARCHAR(10) NOT NULL,
    id              BIGINT      NOT NULL,
    member_id       BIGINT      NOT NULL REFERENCES member(id),
    give_course_no  VARCHAR(20) NOT NULL,
    want_course_no  VARCHAR(20) NOT NULL,
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    PRIMARY KEY (term, id)
) PARTITION BY LIST (term);

CREATE INDEX IF NOT EXISTS idx_intent_member_active
    ON exchange_intent(member_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_intent_matching_pool
    ON exchange_intent(give_course_no, want_course_no)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uidx_active_intent 
    ON exchange_intent (term, member_id, give_course_no, want_course_no) 
    WHERE is_deleted = FALSE;

-- ============================================================
-- 12. exchange_room (파티셔닝)
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_room (
    term        VARCHAR(10) NOT NULL,
    id          BIGINT      NOT NULL,
    cycle_hash  VARCHAR(64) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (term, id),
    CONSTRAINT uniq_term_cycle_hash UNIQUE (term, cycle_hash)
) PARTITION BY LIST (term);

-- ============================================================
-- 13. exchange_room_intent (파티셔닝)
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_room_intent (
    term        VARCHAR(10) NOT NULL,
    room_id     BIGINT      NOT NULL,
    intent_id   BIGINT      NOT NULL,
    member_id   BIGINT      NOT NULL REFERENCES member(id),
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE,
    is_on       BOOLEAN     NOT NULL DEFAULT TRUE,
    joined_at   TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (term, room_id, intent_id),
    FOREIGN KEY (term, room_id) REFERENCES exchange_room(term, id),
    FOREIGN KEY (term, intent_id) REFERENCES exchange_intent(term, id)
) PARTITION BY LIST (term);

CREATE INDEX IF NOT EXISTS idx_room_intent_member
    ON exchange_room_intent(member_id, room_id)
    WHERE is_on = TRUE;

CREATE INDEX IF NOT EXISTS idx_room_intent_reverse
    ON exchange_room_intent(intent_id, room_id);

-- ============================================================
-- 14. exchange_room_message (파티셔닝)
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_room_message (
    term        VARCHAR(10) NOT NULL,
    id          BIGINT      NOT NULL,
    room_id     BIGINT      NOT NULL,
    member_id   BIGINT      NOT NULL REFERENCES member(id),
    intent_id   BIGINT      NOT NULL,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (term, id),
    FOREIGN KEY (term, room_id) REFERENCES exchange_room(term, id),
    FOREIGN KEY (term, intent_id) REFERENCES exchange_intent(term, id)
) PARTITION BY LIST (term);

CREATE INDEX IF NOT EXISTS idx_message_room_id_pagination
    ON exchange_room_message(room_id, id DESC);

-- ============================================================
-- 15. exchange_room_read_status (파티셔닝)
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_room_read_status (
    term                  VARCHAR(10) NOT NULL,
    room_id               BIGINT      NOT NULL,
    member_id             BIGINT      NOT NULL REFERENCES member(id),
    intent_id             BIGINT      NOT NULL,
    last_read_message_id  BIGINT      NOT NULL,
    last_read_at          TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (term, room_id, member_id),
    FOREIGN KEY (term, room_id) REFERENCES exchange_room(term, id),
    FOREIGN KEY (term, intent_id) REFERENCES exchange_intent(term, id),
    FOREIGN KEY (term, last_read_message_id) REFERENCES exchange_room_message(term, id)
) PARTITION BY LIST (term);

CREATE INDEX IF NOT EXISTS idx_read_status_member
    ON exchange_room_read_status(member_id, room_id);

-- ============================================================
-- 16. exchange 파티션 자동 생성
-- ============================================================
DO $$
DECLARE
    start_year INT := 2026;
    end_year INT := 2100;
    current_year INT;
    terms TEXT[] := ARRAY['10', '15', '20', '25'];
    t TEXT;
    target_term TEXT;
BEGIN
    FOR current_year IN start_year..end_year LOOP
        FOREACH t IN ARRAY terms LOOP
            target_term := current_year::TEXT || t;

            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_intent_%I PARTITION OF exchange_intent FOR VALUES IN (%L)', target_term, target_term);
            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_room_%I PARTITION OF exchange_room FOR VALUES IN (%L)', target_term, target_term);
            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_room_intent_%I PARTITION OF exchange_room_intent FOR VALUES IN (%L)', target_term, target_term);
            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_room_message_%I PARTITION OF exchange_room_message FOR VALUES IN (%L)', target_term, target_term);
            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_room_read_status_%I PARTITION OF exchange_room_read_status FOR VALUES IN (%L)', target_term, target_term);
        END LOOP;
    END LOOP;
END $$;
