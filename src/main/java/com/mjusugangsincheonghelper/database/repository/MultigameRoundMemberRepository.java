package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundMemberEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MultigameRoundMemberRepository extends JpaRepository<MultigameRoundMemberEntity, Long> {

	Optional<MultigameRoundMemberEntity> findByStartTimeAndMemberId(String startTime, Long memberId);

	Page<MultigameRoundMemberEntity> findByMemberIdOrderByStartTimeDesc(Long memberId, Pageable pageable);

	@Query("""
			SELECT member.subjectId, COUNT(member),
			       SUM(CASE WHEN member.status = 'SUCCESS' THEN 1 ELSE 0 END)
			FROM MultigameRoundMemberEntity member
			WHERE member.startTime = :startTime
			GROUP BY member.subjectId
			ORDER BY member.subjectId
			""")
	List<Object[]> aggregateBySubject(@Param("startTime") String startTime);

	@Query("""
			SELECT member.memberId, memberDepartment.department,
			       SUM(CASE WHEN member.status = 'SUCCESS' THEN 1 ELSE 0 END),
			       COUNT(member)
			FROM MultigameRoundMemberEntity member
			JOIN Member memberDepartment ON memberDepartment.id = member.memberId
			WHERE memberDepartment.department IS NOT NULL AND memberDepartment.department <> ''
			GROUP BY member.memberId, memberDepartment.department
			""")
	List<Object[]> aggregateByMemberDepartment();

	@Modifying
	@Query("DELETE FROM MultigameRoundMemberEntity member WHERE member.memberId = :oldMemberId AND member.startTime IN (SELECT target.startTime FROM MultigameRoundMemberEntity target WHERE target.memberId = :newMemberId)")
	void deleteConflicting(@Param("oldMemberId") Long oldMemberId, @Param("newMemberId") Long newMemberId);

	@Modifying
	@Query("UPDATE MultigameRoundMemberEntity member SET member.memberId = :newMemberId WHERE member.memberId = :oldMemberId")
	void updateMemberId(@Param("oldMemberId") Long oldMemberId, @Param("newMemberId") Long newMemberId);
}
