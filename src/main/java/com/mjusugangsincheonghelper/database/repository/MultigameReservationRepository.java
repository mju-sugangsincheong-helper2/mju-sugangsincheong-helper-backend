package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MultigameReservationRepository extends JpaRepository<MultigameReservationEntity, Long> {

	List<MultigameReservationEntity> findByStartTime(String startTime);

	boolean existsByStartTimeAndMemberId(String startTime, Long memberId);

	List<MultigameReservationEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
