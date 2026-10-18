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
	 * 전체 구간 분포표(C3 원천). 뷰를 통하지 않는 raw 쿼리.
	 * 행: [sequence, cc×4, cy×4, cok×4, total×4] (p10/p30/p50/p70 순).
	 */
	@Query(value = """
			SELECT d.sequence,
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY d.t_click_course),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY d.t_click_course),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY d.t_click_course),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY d.t_click_course),
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY d.t_click_yes),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY d.t_click_yes),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY d.t_click_yes),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY d.t_click_yes),
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY d.t_click_ok),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY d.t_click_ok),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY d.t_click_ok),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY d.t_click_ok),
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok)
			FROM single_game_detail d
			JOIN single_game sg ON sg.id = d.game_id
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			GROUP BY d.sequence
			ORDER BY d.sequence ASC
			""", nativeQuery = true)
	List<Object[]> findGlobalSequenceBands(@Param("totalCourses") int totalCourses);



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

	/**
	 * 학과 구간 분포표(C4 원천). 전체판과 동형이며 학과 필터만 추가된다.
	 * 행: [sequence, cc×4, cy×4, cok×4, total×4] (p10/p30/p50/p70 순).
	 */
	@Query(value = """
			SELECT d.sequence,
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY d.t_click_course),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY d.t_click_course),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY d.t_click_course),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY d.t_click_course),
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY d.t_click_yes),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY d.t_click_yes),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY d.t_click_yes),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY d.t_click_yes),
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY d.t_click_ok),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY d.t_click_ok),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY d.t_click_ok),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY d.t_click_ok),
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok)
			FROM single_game_detail d
			JOIN single_game sg ON sg.id = d.game_id
			JOIN member m ON m.id = sg.member_id
			WHERE sg.total_courses = :totalCourses
			  AND sg.is_completed = TRUE
			  AND m.department = :department
			GROUP BY d.sequence
			ORDER BY d.sequence ASC
			""", nativeQuery = true)
	List<Object[]> findDeptSequenceBands(@Param("totalCourses") int totalCourses,
	                                      @Param("department") String department);

	/**
	 * 피드백 분포표(C5 원천). 전수 로우를 전송하지 않고 판별 축약값의 분위수만 1행으로 반환한다.
	 * 행: [n, aim×4, burst×4, first×4, pace×4] (p10/p30/p50/p70 순).
	 * pace는 N>=3판만 모집단에 포함된다(NULL은 PERCENTILE_CONT가 무시).
	 * first는 sequence 순서상 첫 구간의 total이다.
	 */
	@Query(value = """
			WITH per_seq AS (
			    SELECT d.game_id,
			           d.t_click_course AS cc,
			           (d.t_click_yes + d.t_click_ok) AS burst,
			           (d.t_click_course + d.t_click_yes + d.t_click_ok) AS total,
			           ROW_NUMBER() OVER (PARTITION BY d.game_id ORDER BY d.sequence ASC) AS rn
			    FROM single_game_detail d
			    JOIN single_game sg ON sg.id = d.game_id
			    WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			),
			per_game AS (
			    SELECT AVG(cc) AS avg_cc,
			           AVG(burst) AS avg_burst,
			           MAX(CASE WHEN rn = 1 THEN total END) AS t1_total,
			           CASE WHEN COUNT(*) >= 3 THEN STDDEV_POP(total) END AS pace_std
			    FROM per_seq
			    GROUP BY game_id
			)
			SELECT COUNT(*) AS n,
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY avg_cc),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY avg_cc),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY avg_cc),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY avg_cc),
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY avg_burst),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY avg_burst),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY avg_burst),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY avg_burst),
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY t1_total),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY t1_total),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY t1_total),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY t1_total),
			       PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY pace_std),
			       PERCENTILE_CONT(0.30) WITHIN GROUP (ORDER BY pace_std),
			       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY pace_std),
			       PERCENTILE_CONT(0.70) WITHIN GROUP (ORDER BY pace_std)
			FROM per_game
			""", nativeQuery = true)
	List<Object[]> findFeedbackBands(@Param("totalCourses") int totalCourses);
}
