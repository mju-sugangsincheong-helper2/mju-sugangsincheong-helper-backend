package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MultigameResultDetailRepository extends JpaRepository<MultigameResultDetailEntity, Long> {

	List<MultigameResultDetailEntity> findByStartTime(String startTime);

	Optional<MultigameResultDetailEntity> findByStartTimeAndMemberId(String startTime, Long memberId);

	Page<MultigameResultDetailEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

	long countByMemberId(Long memberId);

	long countByMemberIdAndStatus(Long memberId, String status);

	@Query(value = """
			SELECT d.subject_id, COUNT(*) as total,
			       SUM(CASE WHEN d.status = 'SUCCESS' THEN 1 ELSE 0 END) as success
			FROM multigame_result_detail d
			WHERE d.member_id = :memberId
			GROUP BY d.subject_id
			ORDER BY total DESC
			""", nativeQuery = true)
	List<Object[]> findSubjectBreakdownByMemberId(@Param("memberId") Long memberId);

	@Query(value = """
			SELECT m.department, COUNT(*) as participation_count,
			       SUM(CASE WHEN d.status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count
			FROM multigame_result_detail d
			JOIN member m ON d.member_id = m.id
			WHERE m.department IS NOT NULL
			GROUP BY m.department
			ORDER BY participation_count DESC
			""", nativeQuery = true)
	List<Object[]> findDepartmentParticipationStats();

	@Query(value = """
			SELECT m.department, COUNT(*) as total_count,
			       SUM(CASE WHEN d.status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count
			FROM multigame_result_detail d
			JOIN member m ON d.member_id = m.id
			WHERE m.department IS NOT NULL
			GROUP BY m.department
			ORDER BY success_count DESC, total_count DESC
			""", nativeQuery = true)
	List<Object[]> findDepartmentSuccessRateStats();
}
