package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MultigameRoundRepository extends JpaRepository<MultigameRoundEntity, String> {

	/** 실제 참여 인원이 있는 라운드 수 (도메인 지표) */
	long countByParticipantCountGreaterThan(int participantCount);

	/** 전체 라운드 중 최대 참여자 수(동시 접속 피크 지표) */
	@Query("SELECT MAX(r.participantCount) FROM MultigameRoundEntity r")
	Optional<Integer> findMaxParticipantCount();

	Page<MultigameRoundEntity> findAllByOrderByStartTimeDesc(Pageable pageable);
}
