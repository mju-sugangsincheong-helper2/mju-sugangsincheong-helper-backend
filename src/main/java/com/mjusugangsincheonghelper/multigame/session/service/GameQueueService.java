package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.common.MultigameLuaScript;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import com.mjusugangsincheonghelper.multigame.session.dto.GameRequestResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameQueueService {

	private final StringRedisTemplate stringRedisTemplate;

	public GameRequestResponse processRequest(String t, Long memberId, int subjectId) {
		List<String> keys = List.of(
				MultigameRedisKeyProvider.state(t),
				MultigameRedisKeyProvider.queue(t),
				MultigameRedisKeyProvider.seq(t),
				MultigameRedisKeyProvider.admissionLimit(t),
				MultigameRedisKeyProvider.seats(t),
				MultigameRedisKeyProvider.history(t),
				MultigameRedisKeyProvider.successMembers(t)
		);

		String timestamp = String.valueOf(Instant.now().toEpochMilli());

		try {
			@SuppressWarnings("unchecked")
			List<Object> result = (List<Object>) stringRedisTemplate.execute(
					MultigameLuaScript.REDIS_SCRIPT,
					keys,
					String.valueOf(memberId),
					String.valueOf(subjectId),
					timestamp
			);

			return mapResult(result);
		} catch (Exception e) {
			throw new BaseException(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR, e);
		}
	}

	private GameRequestResponse mapResult(List<Object> result) {
		if (result == null || result.isEmpty()) {
			throw new BaseException(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR);
		}

		String status = String.valueOf(result.get(0));

		return switch (status) {
			case "BLOCKED" -> GameRequestResponse.builder()
					.status("BLOCKED")
					.currentState(result.size() > 1 ? String.valueOf(result.get(1)) : null)
					.build();
			case "PENDING" -> GameRequestResponse.builder()
					.status("PENDING")
					.seq(toInt(result.get(1)))
					.limit(toInt(result.get(2)))
					.build();
			case "SUCCESS" -> GameRequestResponse.builder()
					.status("SUCCESS")
					.subjectId(toInt(result.get(1)))
					.remaining(toInt(result.get(2)))
					.build();
			case "FAIL_SOLDOUT" -> GameRequestResponse.builder()
					.status("FAIL_SOLDOUT")
					.subjectId(toInt(result.get(1)))
					.build();
			case "FAIL_DUPLICATE" -> GameRequestResponse.builder()
					.status("FAIL_DUPLICATE")
					.build();
			default -> throw new BaseException(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR);
		};
	}

	private Integer toInt(Object value) {
		if (value == null) return null;
		if (value instanceof Number n) return n.intValue();
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
