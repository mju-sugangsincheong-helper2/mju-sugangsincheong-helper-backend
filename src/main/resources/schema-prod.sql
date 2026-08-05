-- 운영 환경 전체 DDL
-- 파티셔닝은 전면 제거(너무 과함 + course 자동 파티션 트리거가 동작하지 않는 문제).
-- 전 테이블 일반 테이블, 교환 도메인은 서로게이트 PK(BIGSERIAL)를 사용한다.

-- ============================================================
-- 0. 확장 기능
-- ============================================================
CREATE EXTENSION IF NOT EXISTS pgmq;

-- ============================================================
-- 1. member
-- ============================================================
CREATE TABLE IF NOT EXISTS member (
    id          BIGSERIAL    PRIMARY KEY,
    role        VARCHAR(20)  NOT NULL,
    position    VARCHAR(50),
    department  VARCHAR(50),
    name        VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_member_role CHECK (role IN ('GUEST', 'MEMBER', 'ADMIN'))
);

-- ============================================================
-- 2. member_auth
-- ============================================================
CREATE TABLE IF NOT EXISTS member_auth (
    id            BIGSERIAL    PRIMARY KEY,
    member_id     BIGINT       NOT NULL UNIQUE REFERENCES member(id) ON DELETE CASCADE,
    auth_type     VARCHAR(20)  NOT NULL,
    auth_key      VARCHAR(255) NOT NULL UNIQUE,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_member_auth_type CHECK (auth_type IN ('GUEST_KEY', 'GOOGLE', 'TEST'))
);

-- ============================================================
-- 3. member_device (refresh token은 SHA-256 해시만 저장)
-- ============================================================
CREATE TABLE IF NOT EXISTS member_device (
    id                      BIGSERIAL    PRIMARY KEY,
    member_id               BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    refresh_token_hash      VARCHAR(64)  NOT NULL UNIQUE,
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
    member_id   BIGINT    PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE,
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
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    content     TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- 7. course
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
);

CREATE INDEX IF NOT EXISTS idx_course_term ON course (term);

-- ============================================================
-- 8. single_game
-- ============================================================
CREATE TABLE IF NOT EXISTS single_game (
    id            BIGSERIAL    PRIMARY KEY,
    member_id     BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
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
    game_id        BIGINT NOT NULL REFERENCES single_game(id) ON DELETE CASCADE,
    sequence       INT    NOT NULL,
    t_click_course INT    NOT NULL,
    t_click_yes    INT    NOT NULL,
    t_click_ok     INT    NOT NULL,
    PRIMARY KEY (game_id, sequence)
);

-- ============================================================
-- 10. multigame
-- ============================================================
CREATE TABLE IF NOT EXISTS multigame_round (
    start_time        VARCHAR(14) PRIMARY KEY,
    participant_count INT         NOT NULL,
    capacity          INT         NOT NULL,
    created_at        TIMESTAMP   NOT NULL DEFAULT now()
);

-- ============================================================
-- 11. multigame_round_member
-- ============================================================
CREATE TABLE IF NOT EXISTS multigame_round_member (
    id          BIGSERIAL   PRIMARY KEY,
    start_time  VARCHAR(14) NOT NULL REFERENCES multigame_round(start_time) ON DELETE CASCADE,
    member_id   BIGINT      NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    subject_id  INT         NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT  uk_multigame_round_member_start_time_member_subject UNIQUE (start_time, member_id, subject_id),
    CONSTRAINT  chk_multigame_round_member_status CHECK (status IN ('SUCCESS', 'FAIL_SOLDOUT'))
);

-- ============================================================
-- 12. multigame_round_log
-- ============================================================
CREATE TABLE IF NOT EXISTS multigame_round_log (
    id             BIGSERIAL   PRIMARY KEY,
    start_time     VARCHAR(14) NOT NULL REFERENCES multigame_round(start_time) ON DELETE CASCADE,
    member_id      BIGINT      NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    subject_id     INT         NOT NULL,
    attempt_status VARCHAR(20) NOT NULL,
    attempt_seq    BIGINT      NOT NULL,
    current_limit  INT         NOT NULL,
    attempted_at   TIMESTAMP   NOT NULL,
    CONSTRAINT  chk_multigame_round_log_attempt_status CHECK (attempt_status IN ('SUCCESS', 'FAIL_SOLDOUT', 'FAIL_DUPLICATE'))
);

CREATE INDEX IF NOT EXISTS idx_multigame_round_member_member_id ON multigame_round_member (member_id);
CREATE INDEX IF NOT EXISTS idx_multigame_round_log_member_id ON multigame_round_log (member_id);

-- ============================================================
-- 13. 뷰
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
-- 14. exchange_intent
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_intent (
    id             BIGSERIAL    PRIMARY KEY,
    term           VARCHAR(10)  NOT NULL,
    member_id      BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    give_course_no VARCHAR(20)  NOT NULL,
    want_course_no VARCHAR(20)  NOT NULL,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_intent_term ON exchange_intent (term);
CREATE INDEX IF NOT EXISTS idx_intent_member_active
    ON exchange_intent (member_id)
    WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_intent_matching_pool
    ON exchange_intent (give_course_no, want_course_no)
    WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uidx_active_intent
    ON exchange_intent (term, member_id, give_course_no, want_course_no)
    WHERE is_deleted = FALSE;

-- ============================================================
-- 15. exchange_room
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_room (
    id          BIGSERIAL    PRIMARY KEY,
    term        VARCHAR(10)  NOT NULL,
    cycle_hash  VARCHAR(64)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uniq_term_cycle_hash UNIQUE (term, cycle_hash),
    CONSTRAINT chk_exchange_room_status CHECK (status IN ('ACTIVE', 'PARTIAL_OFF', 'PARTIAL_DELETE', 'ALL_DELETE'))
);

-- ============================================================
-- 16. exchange_room_intent
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_room_intent (
    term        VARCHAR(10) NOT NULL,
    room_id     BIGINT      NOT NULL REFERENCES exchange_room(id) ON DELETE CASCADE,
    intent_id   BIGINT      NOT NULL REFERENCES exchange_intent(id) ON DELETE CASCADE,
    member_id   BIGINT      NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE,
    is_on       BOOLEAN     NOT NULL DEFAULT TRUE,
    joined_at   TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (term, room_id, intent_id)
);

CREATE INDEX IF NOT EXISTS idx_room_intent_member
    ON exchange_room_intent (member_id, room_id)
    WHERE is_on = TRUE;
CREATE INDEX IF NOT EXISTS idx_room_intent_reverse
    ON exchange_room_intent (intent_id, room_id);

-- ============================================================
-- 17. exchange_room_message
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_room_message (
    id            BIGSERIAL    PRIMARY KEY,
    term          VARCHAR(10)  NOT NULL,
    room_id       BIGINT       NOT NULL REFERENCES exchange_room(id) ON DELETE CASCADE,
    member_id     BIGINT       REFERENCES member(id) ON DELETE CASCADE,
    intent_id     BIGINT       REFERENCES exchange_intent(id) ON DELETE CASCADE,
    message_type  VARCHAR(10)  NOT NULL DEFAULT 'TALK',
    content       TEXT         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_exchange_room_message_type CHECK (
        (message_type = 'TALK' AND member_id IS NOT NULL AND intent_id IS NOT NULL) OR
        (message_type = 'SYSTEM' AND member_id IS NULL AND intent_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_message_room_id_pagination
    ON exchange_room_message (room_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_message_term
    ON exchange_room_message (term);

-- ============================================================
-- 18. exchange_room_read_status
-- ============================================================
CREATE TABLE IF NOT EXISTS exchange_room_read_status (
    term                  VARCHAR(10) NOT NULL,
    room_id               BIGINT      NOT NULL REFERENCES exchange_room(id) ON DELETE CASCADE,
    member_id             BIGINT      NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    intent_id             BIGINT      NOT NULL REFERENCES exchange_intent(id) ON DELETE CASCADE,
    last_read_message_id  BIGINT      NOT NULL DEFAULT 0,
    last_read_at          TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (term, room_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_read_status_member
    ON exchange_room_read_status (member_id, room_id);
