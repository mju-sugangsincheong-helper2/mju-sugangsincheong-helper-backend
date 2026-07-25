package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 개발 환경 전용 게임 초기화 유틸리티
 * 
 * 운영 환경: LifecycleScheduler가 T-5m에 자동으로 게임 초기화
 * 개발 환경: MultigameReservationService가 예약 생성 시 즉시 호출
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("dev")
public class DevGameInitializer {

	private static final int SUBJECT_COUNT = 6;

	private final StringRedisTemplate stringRedisTemplate;

	/**
	 * 게임을 WAITING 상태로 초기화
	 * 
	 * @param multigameId 게임 ID (14자리)
	 * @param participantCount 예상 참여자 수
	 */
	public void initializeGame(String multigameId, int participantCount) {
		int capacity = Math.max(1, participantCount / 2);
		int initialLimit = Math.max(1, (int) Math.floor(participantCount * 0.2));

		Map<String, String> seats = new HashMap<>();
		for (int i = 1; i <= SUBJECT_COUNT; i++) {
			seats.put(String.valueOf(i), String.valueOf(capacity));
		}

		stringRedisTemplate.opsForValue().set(MultigameRedisKeyProvider.state(multigameId), "WAITING");
		stringRedisTemplate.opsForValue().set(MultigameRedisKeyProvider.seq(multigameId), "0");
		stringRedisTemplate.opsForValue().set(MultigameRedisKeyProvider.admissionLimit(multigameId), String.valueOf(initialLimit));
		stringRedisTemplate.opsForHash().putAll(MultigameRedisKeyProvider.seats(multigameId), seats);

		log.info("[DEV] 게임 초기화 완료: multigameId={}, participants={}, capacity={}, initialLimit={}",
				multigameId, participantCount, capacity, initialLimit);
	}

	/**
	 * 게임 상태를 수동으로 전이 (테스트용)
	 */
	public void transitionState(String multigameId, String targetState) {
		String stateKey = MultigameRedisKeyProvider.state(multigameId);
		String currentState = stringRedisTemplate.opsForValue().get(stateKey);

		if (currentState == null) {
			log.warn("[DEV] 게임을 찾을 수 없음: multigameId={}", multigameId);
			return;
		}

		stringRedisTemplate.opsForValue().set(stateKey, targetState);
		log.info("[DEV] 상태 전이: multigameId={}, {} -> {}", multigameId, currentState, targetState);
	}

	/**
	 * 현재 게임 상태 조회
	 */
	public String getState(String multigameId) {
		return stringRedisTemplate.opsForValue().get(MultigameRedisKeyProvider.state(multigameId));
	}
}
