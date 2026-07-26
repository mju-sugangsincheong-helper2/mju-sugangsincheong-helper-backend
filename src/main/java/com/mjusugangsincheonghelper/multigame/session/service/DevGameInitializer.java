package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import com.mjusugangsincheonghelper.multigame.session.domain.GameState;
import com.mjusugangsincheonghelper.multigame.session.domain.MultigameStateEngine;
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

	private final StringRedisTemplate stringRedisTemplate;
	private final MultigameStateEngine stateEngine;
	private final MultigameFinalizeService finalizeService;

	/**
	 * 게임을 WAITING 상태로 초기화
	 * 
	 * @param multigameId 게임 ID (14자리)
	 * @param participantCount 예상 참여자 수
	 */
	public void initializeGame(String multigameId, int participantCount) {
		MultigameRedisKeyProvider.initializeGameSession(stringRedisTemplate, stateEngine, multigameId, participantCount);
		log.info("[DEV] 게임 초기화 완료: multigameId={}, participants={}", multigameId, participantCount);
	}

	/**
	 * 게임 상태를 수동으로 전이 (테스트용)
	 * 문서의 상태 머신을 기반으로 유효한 전이만 허용합니다.
	 */
	public void transitionState(String multigameId, String targetStateStr) {
		GameState targetState = GameState.fromString(targetStateStr);
		if (targetState == null) {
			log.warn("[DEV] 유효하지 않은 상태: targetState={}", targetStateStr);
			return;
		}

		if (targetState == GameState.FINALIZE) {
			finalizeService.finalizeGame(multigameId);
		} else {
			boolean success = stateEngine.transitionTo(multigameId, targetState);
			if (!success) {
				log.warn("[DEV] 상태 전이 실패: multigameId={}, targetState={}", multigameId, targetStateStr);
			}
		}
	}

	/**
	 * 현재 게임 상태 조회
	 */
	public String getState(String multigameId) {
		GameState state = stateEngine.getState(multigameId);
		return state != null ? state.name() : null;
	}
}
