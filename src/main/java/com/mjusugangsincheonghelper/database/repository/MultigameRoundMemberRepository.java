package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundMemberEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MultigameRoundMemberRepository extends JpaRepository<MultigameRoundMemberEntity, Long> {

	/** 한 라운드에서 과목별로 각각 결과 레코드를 가질 수 있으므로 List로 조회한다. */
	List<MultigameRoundMemberEntity> findByStartTimeAndMemberIdOrderBySubjectIdAsc(String startTime, Long memberId);

	/**
	 * 내가 최종 결과를 가진 라운드(start_time) 목록을 최신순으로 페이징 조회한다.
	 * 홈 "최신 N회차"의 페이지네이션 단위는 행(subject)이 아니라 라운드여야 한다.
	 */
	@Query(value = """
			SELECT DISTINCT member.startTime
			FROM MultigameRoundMemberEntity member
			WHERE member.memberId = :memberId
			ORDER BY member.startTime DESC
			""", countQuery = """
			SELECT COUNT(DISTINCT member.startTime)
			FROM MultigameRoundMemberEntity member
			WHERE member.memberId = :memberId
			""")
	Page<String> findDistinctStartTimesByMemberId(@Param("memberId") Long memberId, Pageable pageable);

	/** 라운드 단위 페이지의 과목별 행들을 한 번에 조회한다. */
	List<MultigameRoundMemberEntity> findByStartTimeInAndMemberIdOrderByStartTimeDescSubjectIdAsc(
			Collection<String> startTimes, Long memberId);

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
			       COUNT(DISTINCT member.startTime)
			FROM MultigameRoundMemberEntity member
			JOIN Member memberDepartment ON memberDepartment.id = member.memberId
			WHERE memberDepartment.department IS NOT NULL AND memberDepartment.department <> ''
			GROUP BY member.memberId, memberDepartment.department
			""")
	List<Object[]> aggregateByMemberDepartment();

	/** 전체 결과 행의 SUCCESS / 비-SUCCESS(FAIL_SOLDOUT) 개수 (도메인 지표) */
	@Query("""
			SELECT SUM(CASE WHEN member.status = 'SUCCESS' THEN 1 ELSE 0 END),
			       SUM(CASE WHEN member.status <> 'SUCCESS' THEN 1 ELSE 0 END)
			FROM MultigameRoundMemberEntity member
			""")
	List<Object[]> aggregateOverallResult();

	/** 특정 라운드 집합의 start_time별 SUCCESS / 비-SUCCESS 개수 (도메인 지표) */
	@Query("""
			SELECT member.startTime,
			       SUM(CASE WHEN member.status = 'SUCCESS' THEN 1 ELSE 0 END),
			       SUM(CASE WHEN member.status <> 'SUCCESS' THEN 1 ELSE 0 END)
			FROM MultigameRoundMemberEntity member
			WHERE member.startTime IN :startTimes
			GROUP BY member.startTime
			""")
	List<Object[]> aggregateResultByStartTimes(@Param("startTimes") Collection<String> startTimes);

	@Modifying
	@Query("DELETE FROM MultigameRoundMemberEntity member WHERE member.memberId = :oldMemberId AND member.startTime IN (SELECT target.startTime FROM MultigameRoundMemberEntity target WHERE target.memberId = :newMemberId)")
	void deleteConflicting(@Param("oldMemberId") Long oldMemberId, @Param("newMemberId") Long newMemberId);

	@Modifying
	@Query("UPDATE MultigameRoundMemberEntity member SET member.memberId = :newMemberId WHERE member.memberId = :oldMemberId")
	void updateMemberId(@Param("oldMemberId") Long oldMemberId, @Param("newMemberId") Long newMemberId);
}
