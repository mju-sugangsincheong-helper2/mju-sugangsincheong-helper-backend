package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SingleGameRepository extends JpaRepository<SingleGameEntity, Long> {

	/** 완주(완료)된 싱글게임 기록 수 (도메인 지표) */
	long countByIsCompletedTrue();

	/** 특정 시각 이후 완주 기록 수 (도메인 지표) */
	long countByIsCompletedTrueAndCreatedAtGreaterThanEqual(java.time.Instant instant);

	/** 완주 기록의 평균 총 소요시간(ms) - 전체 종목 통합 (도메인 지표) */
	@Query("SELECT AVG(s.tTotal) FROM SingleGameEntity s WHERE s.isCompleted = true")
	Double averageTTotalByIsCompletedTrue();

	/** 완주 기록 중 최고(최소) 총 소요시간(ms) (도메인 지표) */
	@Query("SELECT MIN(s.tTotal) FROM SingleGameEntity s WHERE s.isCompleted = true")
	Integer minTTotalByIsCompletedTrue();

	/** 종목(과목 수)별 기록 집계: 전체/완주 수, 완주 평균·최단 소요시간(ms) (도메인 지표) */
	@Query("""
			SELECT s.totalCourses,
			       COUNT(s),
			       SUM(CASE WHEN s.isCompleted = true THEN 1 ELSE 0 END),
			       AVG(CASE WHEN s.isCompleted = true THEN s.tTotal END),
			       MIN(CASE WHEN s.isCompleted = true THEN s.tTotal END)
			FROM SingleGameEntity s
			GROUP BY s.totalCourses
			ORDER BY s.totalCourses
			""")
	List<Object[]> aggregateByTotalCourses();

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
			""", nativeQuery = true)
	List<Object[]> findRankingRaw(@Param("totalCourses") int totalCourses);

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
			""", nativeQuery = true)
	List<Object[]> findDeptRankingRaw(@Param("totalCourses") int totalCourses,
	                                  @Param("department") String department);

	@Query(value = """
			SELECT d.game_id, d.sequence,
			       d.t_click_course, d.t_click_yes, d.t_click_ok
			FROM single_game_detail d
			WHERE d.game_id IN (
			    SELECT sg.id FROM single_game sg
			    WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			)
			ORDER BY d.game_id, d.sequence
			""", nativeQuery = true)
	List<Object[]> findAllDetailsByTotalCourses(@Param("totalCourses") int totalCourses);

	@Query(value = """
			SELECT sg.id FROM single_game sg
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			ORDER BY sg.t_total ASC, sg.created_at ASC
			""", nativeQuery = true)
	List<Long> findRankedGameIds(@Param("totalCourses") int totalCourses);

	@Query(value = """
			SELECT sg.id FROM single_game sg
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			  AND sg.t_total <= :tTotal
			""", nativeQuery = true)
	List<Long> findGameIdsWithBetterOrEqualTTotal(@Param("totalCourses") int totalCourses,
	                                              @Param("tTotal") int tTotal);

	long countByTotalCoursesAndIsCompletedTrue(int totalCourses);

	@Query(value = """
			SELECT sg.id, m.name AS member_name, d.t_click_course
			FROM single_game sg
			JOIN member m ON sg.member_id = m.id
			JOIN single_game_detail d ON d.game_id = sg.id
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			  AND d.sequence = 1
			ORDER BY d.t_click_course ASC, sg.created_at ASC
			""", nativeQuery = true)
	List<Object[]> findFirstClickRaw(@Param("totalCourses") int totalCourses);

	@Query(value = """
			SELECT total_courses, sequence,
			       p10_click_course, p30_click_course, p50_click_course, p70_click_course,
			       p10_click_yes, p30_click_yes, p50_click_yes, p70_click_yes,
			       p10_click_ok, p30_click_ok, p50_click_ok, p70_click_ok,
			       p10_total, p30_total, p50_total, p70_total
			FROM v_sequence_percentile_stats
			WHERE total_courses = :totalCourses
			ORDER BY sequence ASC
			""", nativeQuery = true)
	List<Object[]> findSequencePercentileStats(@Param("totalCourses") int totalCourses);

	@Query(value = """
			SELECT sg.id FROM single_game sg
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			  AND sg.t_enter_main <= :tEnterMain
			""", nativeQuery = true)
	List<Long> findGameIdsWithBetterOrEqualEnterMain(@Param("totalCourses") int totalCourses,
	                                                 @Param("tEnterMain") int tEnterMain);

	Optional<SingleGameEntity> findTopByMemberIdAndTotalCoursesAndIsCompletedTrueOrderByCreatedAtDesc(
			Long memberId, int totalCourses);

	@Query(value = """
			SELECT sg.id FROM single_game sg
			JOIN member m ON sg.member_id = m.id
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			  AND m.department = :department
			ORDER BY sg.t_total ASC, sg.created_at ASC
			""", nativeQuery = true)
	List<Long> findDeptRankedGameIds(@Param("totalCourses") int totalCourses,
	                                 @Param("department") String department);

	@Query(value = """
			SELECT DISTINCT m.department
			FROM single_game sg
			JOIN member m ON sg.member_id = m.id
			WHERE sg.is_completed = TRUE AND m.department IS NOT NULL AND m.department != ''
			ORDER BY m.department ASC
			""", nativeQuery = true)
	List<String> findDistinctDepartments();

	@Query(value = """
			SELECT
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY t_enter_main) AS p10,
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY t_enter_main) AS p30,
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY t_enter_main) AS p50,
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY t_enter_main) AS p70
			FROM single_game
			WHERE total_courses = :totalCourses AND is_completed = TRUE
			""", nativeQuery = true)
	List<Object[]> findEnterMainPercentileStats(@Param("totalCourses") int totalCourses);

	@Query(value = """
			SELECT
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY sg.t_enter_main) AS p10,
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY sg.t_enter_main) AS p30,
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY sg.t_enter_main) AS p50,
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY sg.t_enter_main) AS p70
			FROM single_game sg
			JOIN member m ON sg.member_id = m.id
			WHERE sg.total_courses = :totalCourses
			  AND sg.is_completed = TRUE
			  AND m.department = :department
			""", nativeQuery = true)
	List<Object[]> findDeptEnterMainPercentileStats(@Param("totalCourses") int totalCourses,
	                                          @Param("department") String department);

	@Query(value = """
			WITH ranked AS (
			    SELECT d.game_id, d.sequence,
			           d.t_click_course, d.t_click_yes, d.t_click_ok,
			           d.t_click_course + d.t_click_yes + d.t_click_ok AS total,
			           ROW_NUMBER() OVER (PARTITION BY d.sequence ORDER BY d.t_click_course) AS rn_cc,
			           COUNT(*) OVER (PARTITION BY d.sequence) AS cnt_cc,
			           ROW_NUMBER() OVER (PARTITION BY d.sequence ORDER BY d.t_click_yes) AS rn_cy,
			           COUNT(*) OVER (PARTITION BY d.sequence) AS cnt_cy,
			           ROW_NUMBER() OVER (PARTITION BY d.sequence ORDER BY d.t_click_ok) AS rn_cok,
			           COUNT(*) OVER (PARTITION BY d.sequence) AS cnt_cok,
			           ROW_NUMBER() OVER (PARTITION BY d.sequence ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok) AS rn_total,
			           COUNT(*) OVER (PARTITION BY d.sequence) AS cnt_total
			    FROM single_game_detail d
			    JOIN single_game sg ON sg.id = d.game_id
			    JOIN member m ON sg.member_id = m.id
			    WHERE sg.total_courses = :totalCourses
			      AND sg.is_completed = TRUE
			      AND m.department = :department
			)
			SELECT sequence,
			       MAX(CASE WHEN metric = 'cc' AND p = 0.10 THEN val END) AS cc_p10,
			       MAX(CASE WHEN metric = 'cc' AND p = 0.30 THEN val END) AS cc_p30,
			       MAX(CASE WHEN metric = 'cc' AND p = 0.50 THEN val END) AS cc_p50,
			       MAX(CASE WHEN metric = 'cc' AND p = 0.70 THEN val END) AS cc_p70,
			       MAX(CASE WHEN metric = 'cy' AND p = 0.10 THEN val END) AS cy_p10,
			       MAX(CASE WHEN metric = 'cy' AND p = 0.30 THEN val END) AS cy_p30,
			       MAX(CASE WHEN metric = 'cy' AND p = 0.50 THEN val END) AS cy_p50,
			       MAX(CASE WHEN metric = 'cy' AND p = 0.70 THEN val END) AS cy_p70,
			       MAX(CASE WHEN metric = 'cok' AND p = 0.10 THEN val END) AS cok_p10,
			       MAX(CASE WHEN metric = 'cok' AND p = 0.30 THEN val END) AS cok_p30,
			       MAX(CASE WHEN metric = 'cok' AND p = 0.50 THEN val END) AS cok_p50,
			       MAX(CASE WHEN metric = 'cok' AND p = 0.70 THEN val END) AS cok_p70,
			       MAX(CASE WHEN metric = 'total' AND p = 0.10 THEN val END) AS total_p10,
			       MAX(CASE WHEN metric = 'total' AND p = 0.30 THEN val END) AS total_p30,
			       MAX(CASE WHEN metric = 'total' AND p = 0.50 THEN val END) AS total_p50,
			       MAX(CASE WHEN metric = 'total' AND p = 0.70 THEN val END) AS total_p70
			FROM (
			    SELECT sequence, 'cc' AS metric, 0.10 AS p, t_click_course AS val FROM ranked WHERE rn_cc = CEIL(cnt_cc * 0.10)
			    UNION ALL
			    SELECT sequence, 'cc', 0.30, t_click_course FROM ranked WHERE rn_cc = CEIL(cnt_cc * 0.30)
			    UNION ALL
			    SELECT sequence, 'cc', 0.50, t_click_course FROM ranked WHERE rn_cc = CEIL(cnt_cc * 0.50)
			    UNION ALL
			    SELECT sequence, 'cc', 0.70, t_click_course FROM ranked WHERE rn_cc = CEIL(cnt_cc * 0.70)
			    UNION ALL
			    SELECT sequence, 'cy', 0.10, t_click_yes FROM ranked WHERE rn_cy = CEIL(cnt_cy * 0.10)
			    UNION ALL
			    SELECT sequence, 'cy', 0.30, t_click_yes FROM ranked WHERE rn_cy = CEIL(cnt_cy * 0.30)
			    UNION ALL
			    SELECT sequence, 'cy', 0.50, t_click_yes FROM ranked WHERE rn_cy = CEIL(cnt_cy * 0.50)
			    UNION ALL
			    SELECT sequence, 'cy', 0.70, t_click_yes FROM ranked WHERE rn_cy = CEIL(cnt_cy * 0.70)
			    UNION ALL
			    SELECT sequence, 'cok', 0.10, t_click_ok FROM ranked WHERE rn_cok = CEIL(cnt_cok * 0.10)
			    UNION ALL
			    SELECT sequence, 'cok', 0.30, t_click_ok FROM ranked WHERE rn_cok = CEIL(cnt_cok * 0.30)
			    UNION ALL
			    SELECT sequence, 'cok', 0.50, t_click_ok FROM ranked WHERE rn_cok = CEIL(cnt_cok * 0.50)
			    UNION ALL
			    SELECT sequence, 'cok', 0.70, t_click_ok FROM ranked WHERE rn_cok = CEIL(cnt_cok * 0.70)
			    UNION ALL
			    SELECT sequence, 'total', 0.10, total FROM ranked WHERE rn_total = CEIL(cnt_total * 0.10)
			    UNION ALL
			    SELECT sequence, 'total', 0.30, total FROM ranked WHERE rn_total = CEIL(cnt_total * 0.30)
			    UNION ALL
			    SELECT sequence, 'total', 0.50, total FROM ranked WHERE rn_total = CEIL(cnt_total * 0.50)
			    UNION ALL
			    SELECT sequence, 'total', 0.70, total FROM ranked WHERE rn_total = CEIL(cnt_total * 0.70)
			) combined
			GROUP BY sequence
			ORDER BY sequence ASC
			""", nativeQuery = true)
	List<Object[]> findDeptSequencePercentileStats(@Param("totalCourses") int totalCourses,
	                                               @Param("department") String department);
}
