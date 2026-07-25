package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplyEngineService {

	private static final int GAME_DURATION_SECONDS = 20;
	private static final int CRITICAL_THRESHOLD_SECONDS = 4;

	private final StringRedisTemplate stringRedisTemplate;

	public void execute(String t, int totalParticipants) {
		int limit = Math.max(1, (int) Math.floor(totalParticipants * 0.2));
		String limitKey = MultigameRedisKeyProvider.admissionLimit(t);
		String queueKey = MultigameRedisKeyProvider.queue(t);

		stringRedisTemplate.opsForValue().set(limitKey, String.valueOf(limit));

		for (int elapsed = 1; elapsed < GAME_DURATION_SECONDS; elapsed++) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			Long queueLength = stringRedisTemplate.opsForZSet().size(queueKey);
			long L = queueLength != null ? queueLength : 0;

			int remainingTime = GAME_DURATION_SECONDS - elapsed;
			int supply;

			if (L == 0) {
				supply = 0;
			} else if (remainingTime <= CRITICAL_THRESHOLD_SECONDS) {
				supply = (int) Math.ceil((double) L / remainingTime);
			} else {
				supply = (int) Math.ceil((double) L / CRITICAL_THRESHOLD_SECONDS);
			}

			limit = Math.min(totalParticipants, limit + supply);
			stringRedisTemplate.opsForValue().set(limitKey, String.valueOf(limit));

			log.debug("SupplyEngine t={} elapsed={}s L={} supply={} limit={}", t, elapsed, L, supply, limit);
		}
	}
}
