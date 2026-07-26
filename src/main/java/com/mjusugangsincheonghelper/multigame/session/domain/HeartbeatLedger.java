package com.mjusugangsincheonghelper.multigame.session.domain;

import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatLedger {

	private static final long HEARTBEAT_TTL_SECONDS = 6;
	private final StringRedisTemplate stringRedisTemplate;

	public void updateHeartbeat(String t, Long memberId) {
		String key = MultigameRedisKeyProvider.heartbeat(t, memberId);
		stringRedisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(HEARTBEAT_TTL_SECONDS));
	}

	public int countActiveHeartbeats(String t) {
		String pattern = MultigameRedisKeyProvider.heartbeatPattern(t);
		org.springframework.data.redis.core.ScanOptions scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
				.match(pattern)
				.count(100)
				.build();

		try (org.springframework.data.redis.core.Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
			int count = 0;
			while (cursor.hasNext()) {
				cursor.next();
				count++;
			}
			return count;
		} catch (Exception e) {
			log.error("Failed to count active heartbeats for t={}", t, e);
			return 0;
		}
	}

	public void saveParticipantSnapshot(String t, int count) {
		String key = MultigameRedisKeyProvider.participantCount(t);
		stringRedisTemplate.opsForValue().set(key, String.valueOf(count));
		log.info("Saved participant_count snapshot for t={}: {}", t, count);
	}

	public int getParticipantSnapshot(String t) {
		String key = MultigameRedisKeyProvider.participantCount(t);
		String countStr = stringRedisTemplate.opsForValue().get(key);
		if (countStr == null) {
			log.warn("participant_count snapshot not found for t={}", t);
			return 0;
		}
		try {
			return Integer.parseInt(countStr);
		} catch (NumberFormatException e) {
			log.error("Invalid participant_count for t={}: {}", t, countStr);
			return 0;
		}
	}
}
