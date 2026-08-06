package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MultigameRoundRepository extends JpaRepository<MultigameRoundEntity, String> {

	/** 실제 참여 인원이 있는 라운드 수 (도메인 지표) */
	long countByParticipantCountGreaterThan(int participantCount);

	/** 전체 라운드 중 최대 참여자 수(동시 접속 피크 지표) */
	@Query("SELECT MAX(r.participantCount) FROM MultigameRoundEntity r")
	Optional<Integer> findMaxParticipantCount();

	Page<MultigameRoundEntity> findAllByOrderByStartTimeDesc(Pageable pageable);

	/** 실제 진행된 라운드의 시간대(0~23시)별 수 - 어느 시간대에 게임이 주로 열렸는지 (도메인 지표) */
	@Query(value = """
			SELECT SUBSTRING(start_time FROM 9 FOR 2)::int AS hour, COUNT(*)::bigint AS cnt
			FROM multigame_round
			WHERE participant_count > 0
			GROUP BY hour
			ORDER BY hour
			""", nativeQuery = true)
	List<Object[]> countRoundsByHour();

	/** 실제 진행된 라운드의 요일(ISO 1=월 ~ 7=일)별 수 (도메인 지표) */
	@Query(value = """
			SELECT EXTRACT(ISODOW FROM TO_TIMESTAMP(start_time, 'YYYYMMDDHH24MISS'))::int AS dow, COUNT(*)::bigint AS cnt
			FROM multigame_round
			WHERE participant_count > 0
			GROUP BY dow
			ORDER BY dow
			""", nativeQuery = true)
	List<Object[]> countRoundsByDayOfWeek();

	/** 특정 시작시각(이상)에 실제 진행된 라운드의 일자(yyyy-MM-dd)별 수 (도메인 지표) */
	@Query(value = """
			SELECT TO_CHAR(TO_TIMESTAMP(start_time, 'YYYYMMDDHH24MISS'), 'YYYY-MM-DD') AS day, COUNT(*)::bigint AS cnt
			FROM multigame_round
			WHERE participant_count > 0 AND start_time >= :from
			GROUP BY day
			ORDER BY day
			""", nativeQuery = true)
	List<Object[]> countRoundsByDaySince(@Param("from") String from);
}
