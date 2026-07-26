package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.session.domain.GameState;
import com.mjusugangsincheonghelper.multigame.session.domain.MultigameStateEngine;
import com.mjusugangsincheonghelper.multigame.session.dto.GameRequestResponse;
import com.mjusugangsincheonghelper.multigame.session.dto.WaitingRoomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultigameSessionService {

	private final WaitingRoomService waitingRoomService;
	private final GameQueueService gameQueueService;
	private final MultigameStateEngine stateEngine;

	public WaitingRoomResponse enterWaitingRoom(String t, Long memberId) {
		waitingRoomService.updateHeartbeat(t, memberId);

		GameState state = stateEngine.getState(t);

		if (state == null || state == GameState.CANCELLED) {
			throw new BaseException(ErrorCode.MULTIGAME_GAME_CANCELLED);
		}

		int participation = waitingRoomService.countParticipants(t);

		return WaitingRoomResponse.builder()
				.multigameId(t)
				.state(state.name())
				.participation(participation)
				.build();
	}

	public GameRequestResponse requestGame(String t, Long memberId, int subjectId) {
		GameState state = stateEngine.getState(t);

		if (state == null || state == GameState.CANCELLED) {
			throw new BaseException(ErrorCode.MULTIGAME_GAME_CANCELLED);
		}

		if (state != GameState.PROGRESS) {
			return GameRequestResponse.builder()
					.status("WAITING")
					.currentState(state.name())
					.build();
		}

		return gameQueueService.processRequest(t, memberId, subjectId);
	}
}
