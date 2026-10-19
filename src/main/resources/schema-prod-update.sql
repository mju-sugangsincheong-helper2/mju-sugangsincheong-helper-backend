-- 운영 반영용 임시 DDL. single_game_histogram 신규 테이블만 생성한다.
-- schema-prod.sql에 이미 반영되어 있으므로, 이 파일은 기존 운영 DB 1회 적용 후 삭제한다.
CREATE TABLE IF NOT EXISTS single_game_histogram (
    total_courses INT         NOT NULL,
    scope         SMALLINT    NOT NULL,
    department    VARCHAR(50) NOT NULL DEFAULT '',
    sequence      INT         NOT NULL,
    metric        SMALLINT    NOT NULL,
    bucket_ms     INT         NOT NULL,
    cnt           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (total_courses, scope, department, sequence, metric, bucket_ms)
);
