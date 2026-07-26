package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRoomService {

	private static final long HEARTBEAT_TTL_SECONDS = 6;

	private final StringRedisTemplate stringRedisTemplate;

	public void updateHeartbeat(String t, Long memberId) {
		String key = MultigameRedisKeyProvider.heartbeat(t, memberId);
		stringRedisTemplate.opsForValue().set(key, "1",
				java.time.Duration.ofSeconds(HEARTBEAT_TTL_SECONDS));
	}

	public int countParticipants(String t) {
		String pattern = MultigameRedisKeyProvider.heartbeatPattern(t);
		ScanOptions scanOptions = ScanOptions.scanOptions()
				.match(pattern)
				.count(100)
				.build();

		try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
			int count = 0;
			while (cursor.hasNext()) {
				cursor.next();
				count++;
			}
			return count;
		} catch (Exception e) {
			log.error("Failed to count heartbeat participants for t={}", t, e);
			return 0;
		}
	}
}
