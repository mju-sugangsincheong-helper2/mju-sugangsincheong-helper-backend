package com.mjusugangsincheonghelper.multigame.session.domain;

import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MultigameStateEngine {

	private final StringRedisTemplate stringRedisTemplate;
	private final AdvisoryLockService advisoryLockService;

	public GameState getState(String t) {
		String stateStr = stringRedisTemplate.opsForValue().get(MultigameRedisKeyProvider.state(t));
		return GameState.fromString(stateStr);
	}

	public void setState(String t, GameState state) {
		if (state == null) return;
		stringRedisTemplate.opsForValue().set(MultigameRedisKeyProvider.state(t), state.name());
	}

	public void cancelGame(String t) {
		setState(t, GameState.CANCELLED);
	}

	public boolean transitionTo(String t, GameState targetState) {
		GameState currentState = getState(t);
		if (currentState == null) {
			log.warn("Game {} state is null when transitioning to {}", t, targetState);
			return false;
		}

		if (!currentState.canTransitionTo(targetState)) {
			log.warn("Invalid state transition for game {}: {} -> {}", t, currentState, targetState);
			return false;
		}

		setState(t, targetState);
		log.info("Game {} state transition: {} -> {}", t, currentState, targetState);
		return true;
	}

	public void tryExecuteWithLock(String jobName, String t, Runnable action) {
		if (!advisoryLockService.tryXactLock(jobName, t)) {
			return;
		}
		try {
			action.run();
		} finally {
			log.debug("{}: completed for {}", jobName, t);
		}
	}

	public void tryExecuteWithSessionLock(String jobName, String t, Runnable action) {
		if (!advisoryLockService.trySessionLock(jobName, t)) {
			return;
		}
		try {
			action.run();
		} finally {
			advisoryLockService.releaseSessionLock(jobName, t);
			log.debug("{}: completed for {}", jobName, t);
		}
	}
}
