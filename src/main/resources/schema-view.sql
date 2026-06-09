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
