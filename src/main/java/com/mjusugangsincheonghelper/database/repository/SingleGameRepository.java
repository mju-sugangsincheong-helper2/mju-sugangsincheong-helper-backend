package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SingleGameRepository extends JpaRepository<SingleGameEntity, Long> {

	@Modifying(clearAutomatically = true)
	@Query("UPDATE SingleGameEntity s SET s.memberId = :newMemberId WHERE s.memberId = :oldMemberId")
	void updateMemberId(@Param("oldMemberId") Long oldMemberId, @Param("newMemberId") Long newMemberId);

	Page<SingleGameEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

	long countByMemberId(Long memberId);

	@Query(value = """
			WITH ranked AS (
			    SELECT sg.id, sg.member_id, m.name AS member_name, m.department,
			           sg.total_courses, sg.t_total, sg.t_enter_main, sg.created_at,
			           ROW_NUMBER() OVER (PARTITION BY sg.member_id ORDER BY sg.t_total ASC, sg.created_at ASC) AS rn
			    FROM single_game sg
			    JOIN member m ON sg.member_id = m.id
			    WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			)
			SELECT id, member_id, member_name, department,
			       total_courses, t_total, t_enter_main, created_at
			FROM ranked
			WHERE rn = 1
			ORDER BY t_total ASC, created_at ASC
			LIMIT :limit
			""", nativeQuery = true)
	List<Object[]> findRankingRaw(@Param("totalCourses") int totalCourses,
	                                @Param("limit") int limit);

	@Query(value = """
			WITH ranked AS (
			    SELECT sg.id, sg.member_id, m.name AS member_name, m.department,
			           sg.total_courses, sg.t_total, sg.t_enter_main, sg.created_at,
			           ROW_NUMBER() OVER (PARTITION BY sg.member_id ORDER BY sg.t_total ASC, sg.created_at ASC) AS rn
			    FROM single_game sg
			    JOIN member m ON sg.member_id = m.id
			    WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			      AND m.department = :department
			)
			SELECT id, member_id, member_name, department,
			       total_courses, t_total, t_enter_main, created_at
			FROM ranked
			WHERE rn = 1
			ORDER BY t_total ASC, created_at ASC
			LIMIT :limit
			""", nativeQuery = true)
	List<Object[]> findDeptRankingRaw(@Param("totalCourses") int totalCourses,
	                                  @Param("department") String department,
	                                  @Param("limit") int limit);

	@Query(value = """
			SELECT COUNT(DISTINCT sg.member_id) FROM single_game sg
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			""", nativeQuery = true)
	long countDistinctPersons(@Param("totalCourses") int totalCourses);

	@Query(value = """
			SELECT COUNT(DISTINCT sg.member_id) FROM single_game sg
			JOIN member m ON sg.member_id = m.id
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			  AND m.department = :department
			""", nativeQuery = true)
	long countDistinctDeptPersons(@Param("totalCourses") int totalCourses,
	                                @Param("department") String department);

	/**
	 * 랭킹은 사람 기준(00 §5): 인당 대표 1판(최고 기록, 동점은 가장 이른 판).
	 * 내 대표판 1행을 가져온다. 없으면 null.
	 */
	@Query(value = """
			SELECT sg.id, sg.t_total, sg.t_enter_main FROM single_game sg
			WHERE sg.member_id = :memberId
			  AND sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			ORDER BY sg.t_total ASC, sg.created_at ASC
			LIMIT 1
			""", nativeQuery = true)
	List<Object[]> findMyBestGame(@Param("memberId") Long memberId,
	                                @Param("totalCourses") int totalCourses);

	@Query(value = """
			SELECT COUNT(*) FROM (
			    SELECT sg.member_id FROM single_game sg
			    WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			    GROUP BY sg.member_id
			    HAVING MIN(sg.t_total) < :best
			) t
			""", nativeQuery = true)
	long countBetterPersons(@Param("totalCourses") int totalCourses,
	                          @Param("best") int best);

	@Query(value = """
			SELECT COUNT(*) FROM (
			    SELECT sg.member_id FROM single_game sg
			    JOIN member m ON m.id = sg.member_id
			    WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			      AND m.department = :department
			    GROUP BY sg.member_id
			    HAVING MIN(sg.t_total) < :best
			) t
			""", nativeQuery = true)
	long countBetterDeptPersons(@Param("totalCourses") int totalCourses,
	                              @Param("department") String department,
	                              @Param("best") int best);

	/**
	 * 전체 구간 버킷분위수표(5ms) — SequenceQuantileTable 원천.
	 * 행: [sequence, AIM×7, CONFIRM×7, COMPLETE×7, TOTAL×7] (Q05/Q10/Q30/Q50/Q70/Q90/Q95 순), 총 29컬럼.
	 * 값은 5ms 버킷 중앙값(하한+2.5)이다. 전수 PERCENTILE_CONT 대비 오차 ≤4.5ms(실측 최대).
		 */
	@Query(value = """
			WITH vals AS (
			    SELECT d.sequence AS seq,
			           (d.t_click_course / 5) * 5 AS b1,
			           (d.t_click_yes / 5) * 5 AS b2,
			           (d.t_click_ok / 5) * 5 AS b3,
			           ((d.t_click_course + d.t_click_yes + d.t_click_ok) / 5) * 5 AS b4
			    FROM single_game_detail d
			    JOIN single_game sg ON sg.id = d.game_id
			    WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			),
			u AS (
			    SELECT seq, m, bk
			    FROM vals,
			    LATERAL (VALUES (b1, 1), (b2, 2), (b3, 3), (b4, 4)) AS v(bk, m)
			),
			b AS (
			    SELECT seq, m, bk, count(*) AS c
			    FROM u
			    GROUP BY 1, 2, 3
			),
			cum AS (
			    SELECT seq, m, bk,
			           sum(c) OVER (PARTITION BY seq, m ORDER BY bk) AS run,
			           sum(c) OVER (PARTITION BY seq, m) AS s
			    FROM b
			)
			SELECT seq,
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.95 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.95 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.95 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.95 * s)
			FROM cum
			GROUP BY seq
			ORDER BY seq ASC
			""", nativeQuery = true)
	List<Object[]> findGlobalSegmentQuantiles(@Param("totalCourses") int totalCourses);

	


	@Query(value = """
			SELECT DISTINCT m.department
			FROM single_game sg
			JOIN member m ON sg.member_id = m.id
			WHERE sg.is_completed = TRUE AND m.department IS NOT NULL AND m.department != ''
			ORDER BY m.department ASC
			""", nativeQuery = true)
	List<String> findDistinctDepartments();

	@Query(value = """
			WITH b AS (
			    SELECT (t_enter_main / 5) * 5 AS bk, count(*) AS c
			    FROM single_game
			    WHERE total_courses = :totalCourses AND is_completed = TRUE
			    GROUP BY 1
			),
			cum AS (
			    SELECT bk,
			           sum(c) OVER (ORDER BY bk) AS run,
			           sum(c) OVER () AS s
			    FROM b
			)
			SELECT min(bk + 2.5) FILTER (WHERE run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.95 * s)
			FROM cum
			""", nativeQuery = true)
	List<Object[]> findGlobalEnterQuantiles(@Param("totalCourses") int totalCourses);

	
	@Query(value = """
			WITH b AS (
			    SELECT (sg.t_enter_main / 5) * 5 AS bk, count(*) AS c
			    FROM single_game sg
			    JOIN member m ON sg.member_id = m.id
			    WHERE sg.total_courses = :totalCourses
			      AND sg.is_completed = TRUE
			      AND m.department = :department
			    GROUP BY 1
			),
			cum AS (
			    SELECT bk,
			           sum(c) OVER (ORDER BY bk) AS run,
			           sum(c) OVER () AS s
			    FROM b
			)
			SELECT min(bk + 2.5) FILTER (WHERE run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE run >= 0.95 * s)
			FROM cum
			""", nativeQuery = true)
	List<Object[]> findDepartmentEnterQuantiles(@Param("totalCourses") int totalCourses,
	                                          @Param("department") String department);


	/**
	 * 학과 구간 버킷분위수표(5ms) — SequenceQuantileTable 학과판 원천.
	 * 행: [sequence, AIM×7, CONFIRM×7, COMPLETE×7, TOTAL×7], 총 29컬럼.
	 * 값은 5ms 버킷 중앙값(하한+2.5)이다. 전수 PERCENTILE_CONT 대비 오차 ≤4.5ms(실측 최대).
	 */
	@Query(value = """
			WITH vals AS (
			    SELECT d.sequence AS seq,
			           (d.t_click_course / 5) * 5 AS b1,
			           (d.t_click_yes / 5) * 5 AS b2,
			           (d.t_click_ok / 5) * 5 AS b3,
			           ((d.t_click_course + d.t_click_yes + d.t_click_ok) / 5) * 5 AS b4
			    FROM single_game_detail d
			    JOIN single_game sg ON sg.id = d.game_id
			    JOIN member m ON m.id = sg.member_id
			    WHERE sg.total_courses = :totalCourses
			      AND sg.is_completed = TRUE
			      AND m.department = :department
			),
			u AS (
			    SELECT seq, m, bk
			    FROM vals,
			    LATERAL (VALUES (b1, 1), (b2, 2), (b3, 3), (b4, 4)) AS v(bk, m)
			),
			b AS (
			    SELECT seq, m, bk, count(*) AS c
			    FROM u
			    GROUP BY 1, 2, 3
			),
			cum AS (
			    SELECT seq, m, bk,
			           sum(c) OVER (PARTITION BY seq, m ORDER BY bk) AS run,
			           sum(c) OVER (PARTITION BY seq, m) AS s
			    FROM b
			)
			SELECT seq,
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.95 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.95 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.95 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.95 * s)
			FROM cum
			GROUP BY seq
			ORDER BY seq ASC
			""", nativeQuery = true)
	List<Object[]> findDepartmentSegmentQuantiles(@Param("totalCourses") int totalCourses,
	                                      @Param("department") String department);


	
	/**
	 * 피드백 버킷분위수표(5ms) — FeedbackQuantileTable 원천.
	 * 행: [n, aimAvgx7, burstAvgx7, firstTotalx7, paceStdx7] (Q05/Q10/Q30/Q50/Q70/Q90/Q95 순), 총 29컬럼.
	 * 값은 5ms 버킷 중앙값(하한+2.5)이다. 전수 PERCENTILE_CONT 대비 오차 ≤4.5ms(실측 최대).
	 */
	@Query(value = """
			WITH agg AS (
			    SELECT d.game_id,
			           AVG(d.t_click_course) AS avg_cc,
			           AVG(d.t_click_yes + d.t_click_ok) AS avg_burst,
			           COUNT(*) AS n,
			           STDDEV_POP(d.t_click_course + d.t_click_yes + d.t_click_ok) AS pace_std_all
			    FROM single_game_detail d
			    JOIN single_game sg ON sg.id = d.game_id
			    WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			    GROUP BY d.game_id
			),
			firsts AS (
			    SELECT d.game_id,
			           (d.t_click_course + d.t_click_yes + d.t_click_ok) AS t1_total
			    FROM single_game_detail d
			    JOIN single_game sg ON sg.id = d.game_id
			    WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			      AND d.sequence = 1
			),
			per_game AS MATERIALIZED (
			    SELECT a.avg_cc,
			           a.avg_burst,
			           f.t1_total,
			           CASE WHEN a.n >= 3 THEN a.pace_std_all END AS pace_std
			    FROM agg a
			    LEFT JOIN firsts f ON f.game_id = a.game_id
			),
			b AS (
			    SELECT (avg_cc / 5) * 5 AS bk, 1 AS m
			    FROM per_game WHERE avg_cc IS NOT NULL
			    UNION ALL
			    SELECT (avg_burst / 5) * 5 AS bk, 2 AS m
			    FROM per_game WHERE avg_burst IS NOT NULL
			    UNION ALL
			    SELECT (t1_total / 5) * 5 AS bk, 3 AS m
			    FROM per_game WHERE t1_total IS NOT NULL
			    UNION ALL
			    SELECT floor(pace_std / 5) * 5 AS bk, 4 AS m
			    FROM per_game WHERE pace_std IS NOT NULL
			),
			cum AS (
			    SELECT m, bk,
			           sum(cnt) OVER (PARTITION BY m ORDER BY bk) AS run,
			           sum(cnt) OVER (PARTITION BY m) AS s
			    FROM (SELECT m, bk, count(*) AS cnt FROM b GROUP BY 1, 2) g
			)
			SELECT (SELECT count(*) FROM per_game) AS n,
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 1 AND run >= 0.95 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 2 AND run >= 0.95 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 3 AND run >= 0.95 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.05 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.10 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.30 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.50 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.70 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.90 * s),
			       min(bk + 2.5) FILTER (WHERE m = 4 AND run >= 0.95 * s)
			FROM cum
			""", nativeQuery = true)
	List<Object[]> findFeedbackQuantiles(@Param("totalCourses") int totalCourses);
}
