package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MultigameRoundLogRepository extends JpaRepository<MultigameRoundLogEntity, Long> {

	List<MultigameRoundLogEntity> findByStartTimeOrderByAttemptedAtAsc(String startTime);

	List<MultigameRoundLogEntity> findByStartTimeAndMemberIdOrderByAttemptedAtAsc(String startTime, Long memberId);

	@Modifying
	@Query("DELETE FROM MultigameRoundLogEntity log WHERE log.startTime = :startTime")
	void deleteAllByStartTime(@Param("startTime") String startTime);
}
