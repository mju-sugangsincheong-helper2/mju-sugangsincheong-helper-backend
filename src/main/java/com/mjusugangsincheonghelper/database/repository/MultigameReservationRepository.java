package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MultigameReservationRepository extends JpaRepository<MultigameReservationEntity, Long> {

	@Modifying(clearAutomatically = true)
	@Query("DELETE FROM MultigameReservationEntity r WHERE r.memberId = :oldMemberId AND r.startTime IN (SELECT r2.startTime FROM MultigameReservationEntity r2 WHERE r2.memberId = :newMemberId)")
	void deleteConflicting(@Param("oldMemberId") Long oldMemberId, @Param("newMemberId") Long newMemberId);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE MultigameReservationEntity r SET r.memberId = :newMemberId WHERE r.memberId = :oldMemberId")
	void updateMemberId(@Param("oldMemberId") Long oldMemberId, @Param("newMemberId") Long newMemberId);

	List<MultigameReservationEntity> findByStartTime(String startTime);

	List<MultigameReservationEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
