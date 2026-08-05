package com.mjusugangsincheonghelper.multigame.game.service;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.game.domain.GameStatus;
import com.mjusugangsincheonghelper.multigame.game.domain.GameStatusResolver;
import com.mjusugangsincheonghelper.multigame.game.domain.RoundTime;
import com.mjusugangsincheonghelper.multigame.game.dto.GameApplyResponse;
import com.mjusugangsincheonghelper.multigame.game.dto.GameEnterResponse;
import com.mjusugangsincheonghelper.multigame.game.dto.GameWaitingResponse;
import com.mjusugangsincheonghelper.multigame.game.runtime.GameApplyScript;
import com.mjusugangsincheonghelper.multigame.game.runtime.GameRuntimeStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameSessionService {

	private final GameRuntimeStore runtimeStore;
	private final StringRedisTemplate redis;
	private final GameStatusResolver statusResolver;

	public GameWaitingResponse waitingRoom(long memberId) {
		LocalDateTime now = LocalDateTime.now();
		GameStatus status = status(now);
		long participation = participationFor(status);
		if (status == GameStatus.WAITING || status == GameStatus.READY) {
			runtimeStore.recordHeartbeat(memberId, Instant.now());
			participation = status == GameStatus.WAITING ? runtimeStore.removeExpiredHeartbeatsAndCount(Instant.now()) : runtimeStore.waitingCount();
		}
		return GameWaitingResponse.builder().multigameId(RoundTime.target(now)).state(status.name()).participation(participation).build();
	}

	public GameEnterResponse enter(long memberId) {
		LocalDateTime now = LocalDateTime.now();
		GameStatus status = status(now);
		assertProgress(status);
		return GameEnterResponse.builder()
				.multigameId(RoundTime.target(now))
				.state(status.name())
				.participation(runtimeStore.enter(memberId))
				.build();
	}

	public void leave(long memberId) {
		runtimeStore.leave(memberId);
	}

	public GameApplyResponse apply(long memberId, int subjectId) {
		GameStatus status = status(LocalDateTime.now());
		if (status != GameStatus.PROGRESS) {
			return GameApplyResponse.builder().status("BLOCKED").currentState(status.name()).build();
		}
		if (!runtimeStore.hasEntered(memberId)) {
			throw new BaseException(ErrorCode.MULTIGAME_GAME_INVALID_STATE, "Enter the game before applying.");
		}
		try {
			@SuppressWarnings("unchecked")
			List<Object> result = (List<Object>) redis.execute(GameApplyScript.SCRIPT, runtimeStore.applyKeys(),
					String.valueOf(memberId), String.valueOf(subjectId), String.valueOf(Instant.now().toEpochMilli()));
			return toResponse(result);
		} catch (BaseException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new BaseException(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR, exception);
		}
	}

	private GameStatus status(LocalDateTime now) {
		GameStatus withoutRedis = statusResolver.resolve(now, null);
		return withoutRedis == GameStatus.STARTING ? statusResolver.resolve(now, runtimeStore.state()) : withoutRedis;
	}

	private long participationFor(GameStatus status) {
		return switch (status) {
			case READY -> runtimeStore.waitingCount();
			case PROGRESS -> runtimeStore.participants();
			default -> 0;
		};
	}

	private void assertProgress(GameStatus status) {
		if (status == GameStatus.CANCELLED) {
			throw new BaseException(ErrorCode.MULTIGAME_GAME_CANCELLED);
		}
		if (status != GameStatus.PROGRESS) {
			throw new BaseException(ErrorCode.MULTIGAME_GAME_INVALID_STATE);
		}
	}

	private GameApplyResponse toResponse(List<Object> result) {
		if (result == null || result.isEmpty()) {
			throw new BaseException(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR);
		}
		String status = String.valueOf(result.getFirst());
		return switch (status) {
			case "BLOCKED" -> GameApplyResponse.builder().status(status).currentState(value(result, 1)).build();
			case "PENDING" -> GameApplyResponse.builder().status(status).seq(number(result, 1)).limit(number(result, 2)).rank(number(result, 3)).build();
			case "SUCCESS" -> GameApplyResponse.builder().status(status).subjectId(number(result, 1).intValue()).remaining(number(result, 2).intValue()).build();
			case "FAIL_SOLDOUT", "FAIL_DUPLICATE" -> GameApplyResponse.builder().status(status).subjectId(number(result, 1).intValue()).build();
			default -> throw new BaseException(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR);
		};
	}

	private String value(List<Object> values, int index) {
		return values.size() > index ? String.valueOf(values.get(index)) : null;
	}

	private Long number(List<Object> values, int index) {
		if (values.size() <= index) {
			throw new BaseException(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR);
		}
		Object value = values.get(index);
		return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
	}
}
