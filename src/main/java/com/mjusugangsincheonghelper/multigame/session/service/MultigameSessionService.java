package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import com.mjusugangsincheonghelper.multigame.session.dto.GameRequestResponse;
import com.mjusugangsincheonghelper.multigame.session.dto.WaitingRoomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultigameSessionService {

	private final StringRedisTemplate stringRedisTemplate;
	private final WaitingRoomService waitingRoomService;
	private final GameQueueService gameQueueService;

	public WaitingRoomResponse enterWaitingRoom(String t, Long memberId) {
		waitingRoomService.updateHeartbeat(t, memberId);

		String stateKey = MultigameRedisKeyProvider.state(t);
		String state = stringRedisTemplate.opsForValue().get(stateKey);

		if (state == null || "CANCELLED".equals(state)) {
			throw new BaseException(ErrorCode.MULTIGAME_GAME_CANCELLED);
		}

		int participation = waitingRoomService.countParticipants(t);

		return WaitingRoomResponse.builder()
				.multigameId(t)
				.state(state)
				.participation(participation)
				.build();
	}

	public GameRequestResponse requestGame(String t, Long memberId, int subjectId) {
		String stateKey = MultigameRedisKeyProvider.state(t);
		String state = stringRedisTemplate.opsForValue().get(stateKey);

		if (state == null || "CANCELLED".equals(state)) {
			throw new BaseException(ErrorCode.MULTIGAME_GAME_CANCELLED);
		}

		if (!"PROGRESS".equals(state)) {
			return GameRequestResponse.builder()
					.status("WAITING")
					.currentState(state)
					.build();
		}

		return gameQueueService.processRequest(t, memberId, subjectId);
	}
}
