package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.SingleGameHistogramEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameHistogramId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SingleGameHistogramRepository
		extends JpaRepository<SingleGameHistogramEntity, SingleGameHistogramId> {

	/**
	 * 버킷 카운터 +1. 같은 키가 있으면 증가, 없으면 생성한다. 동시 완료에도 원자적으로 동작한다.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO single_game_histogram
				(total_courses, scope, department, sequence, metric, bucket_ms, cnt)
			VALUES (:totalCourses, :scope, :department, :sequence, :metric, :bucketMs, 1)
			ON CONFLICT (total_courses, scope, department, sequence, metric, bucket_ms)
			DO UPDATE SET cnt = single_game_histogram.cnt + 1
			""", nativeQuery = true)
	void increment(@Param("totalCourses") int totalCourses,
			@Param("scope") short scope,
			@Param("department") String department,
			@Param("sequence") int sequence,
			@Param("metric") short metric,
			@Param("bucketMs") int bucketMs);

	/**
	 * 전수 백필. single_game_detail 전체를 5ms 버킷으로 집계해 히스토그램을 처음부터 채운다.
	 * 증분 경로(Java 계산)와 동일한 버킷 규칙을 쓴다: 정수는 (v/5)*5, 평균·표준편차는 floor(v/5)*5.
	 * 완료 판만 대상이며, 호출 전 TRUNCATE된 비어 있는 테이블을 전제로 한다.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO single_game_histogram
				(total_courses, scope, department, sequence, metric, bucket_ms, cnt)
			WITH vals AS (
			    SELECT sg.total_courses AS tc,
			           m.department AS dept,
			           d.game_id AS gid,
			           d.sequence AS seq,
			           (d.t_click_course / 5) * 5 AS b1,
			           (d.t_click_yes / 5) * 5 AS b2,
			           (d.t_click_ok / 5) * 5 AS b3,
			           ((d.t_click_course + d.t_click_yes + d.t_click_ok) / 5) * 5 AS b4,
			           sg.t_enter_main AS enter_main,
			           d.t_click_course AS cc,
			           (d.t_click_yes + d.t_click_ok) AS burst,
			           (d.t_click_course + d.t_click_yes + d.t_click_ok) AS total,
			           MIN(d.sequence) OVER (PARTITION BY d.game_id) AS min_seq
			    FROM single_game_detail d
			    JOIN single_game sg ON sg.id = d.game_id
			    JOIN member m ON m.id = sg.member_id
			    WHERE sg.is_completed = TRUE
			),
			seg AS (
			    SELECT tc, dept, seq, m, bk, count(*) AS c
			    FROM vals,
			    LATERAL (VALUES (b1, 1), (b2, 2), (b3, 3), (b4, 4)) AS v(bk, m)
			    GROUP BY 1, 2, 3, 4, 5
			),
			ent AS (
			    SELECT tc, dept,
			           (enter_main / 5) * 5 AS bk,
			           count(*) AS c
			    FROM (SELECT DISTINCT tc, dept, gid, enter_main FROM vals) g
			    GROUP BY 1, 2, 3
			),
			per_game AS MATERIALIZED (
			    SELECT tc, dept, gid,
			           AVG(cc) AS avg_cc,
			           AVG(burst) AS avg_burst,
			           MAX(total) FILTER (WHERE seq = min_seq) AS t1_total,
			           CASE WHEN COUNT(*) >= 3 THEN STDDEV_POP(total) END AS pace_std
			    FROM vals
			    GROUP BY 1, 2, 3
			),
			fb AS (
			    SELECT tc, m, bk, count(*) AS c
			    FROM per_game,
			    LATERAL (VALUES (floor(avg_cc / 5) * 5, 1),
			                    (floor(avg_burst / 5) * 5, 2),
			                    ((t1_total / 5) * 5, 3),
			                    (floor(pace_std / 5) * 5, 4)) AS v(bk, m)
			    WHERE (m <> 3 OR t1_total IS NOT NULL)
			      AND (m <> 4 OR pace_std IS NOT NULL)
			    GROUP BY 1, 2, 3
			)
			SELECT tc, 0::smallint, '', seq, m::smallint, bk, SUM(c)::bigint FROM seg GROUP BY 1, 2, 3, 4, 5, 6
			UNION ALL
			SELECT tc, 1::smallint, dept, seq, m::smallint, bk, SUM(c)::bigint FROM seg GROUP BY 1, 2, 3, 4, 5, 6
			UNION ALL
			SELECT tc, 0::smallint, '', 0, 4::smallint, bk, SUM(c)::bigint FROM ent GROUP BY 1, 2, 3, 4, 5, 6
			UNION ALL
			SELECT tc, 1::smallint, dept, 0, 4::smallint, bk, SUM(c)::bigint FROM ent GROUP BY 1, 2, 3, 4, 5, 6
			UNION ALL
			SELECT tc, 2::smallint, '', 0, m::smallint, bk, SUM(c)::bigint FROM fb GROUP BY 1, 2, 3, 4, 5, 6
			""", nativeQuery = true)
	void backfillFromDetails();

	@Modifying
	@Query(value = "TRUNCATE single_game_histogram", nativeQuery = true)
	void truncateAll();
}
