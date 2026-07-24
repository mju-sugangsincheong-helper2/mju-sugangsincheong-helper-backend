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

	@Modifying(clearAutomatically = true)
	@Query("UPDATE SingleGameEntity s SET s.memberId = :newMemberId WHERE s.memberId = :oldMemberId")
	void updateMemberId(@Param("oldMemberId") Long oldMemberId, @Param("newMemberId") Long newMemberId);

	Page<SingleGameEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

	long countByMemberId(Long memberId);

	@Query(value = """
			SELECT sg.id, sg.member_id, m.name AS member_name, m.department,
			       sg.total_courses, sg.t_total, sg.t_enter_main, sg.created_at
			FROM single_game sg
			JOIN member m ON sg.member_id = m.id
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			ORDER BY sg.t_total ASC, sg.created_at ASC
			""", nativeQuery = true)
	List<Object[]> findRankingRaw(@Param("totalCourses") int totalCourses);

	@Query(value = """
			SELECT sg.id, sg.member_id, m.name AS member_name, m.department,
			       sg.total_courses, sg.t_total, sg.t_enter_main, sg.created_at
			FROM single_game sg
			JOIN member m ON sg.member_id = m.id
			WHERE sg.total_courses = :totalCourses AND sg.is_completed = TRUE
			  AND m.department = :department
			ORDER BY sg.t_total ASC, sg.created_at ASC
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
			SELECT * FROM v_sequence_percentile_stats
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
}
