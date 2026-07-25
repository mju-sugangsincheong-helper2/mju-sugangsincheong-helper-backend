package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
		Set<String> keys = stringRedisTemplate.keys(pattern);
		return keys != null ? keys.size() : 0;
	}
}
