package com.mjusugangsincheonghelper.multigame.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.session.domain.GameState;
import com.mjusugangsincheonghelper.multigame.session.domain.MultigameStateEngine;
import com.mjusugangsincheonghelper.multigame.session.dto.GameRequestResponse;
import com.mjusugangsincheonghelper.multigame.session.dto.WaitingRoomResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameSessionService 테스트")
class MultigameSessionServiceTest {

	@Mock
	private WaitingRoomService waitingRoomService;

	@Mock
	private GameQueueService gameQueueService;

	@Mock
	private MultigameStateEngine stateEngine;

	@InjectMocks
	private MultigameSessionService sessionService;

	@Nested
	@DisplayName("enterWaitingRoom 메서드는")
	class Describe_enterWaitingRoom {

		@Test
		@DisplayName("WAITING 상태이면 대기방 정보를 반환한다")
		void it_returns_waiting_room_info_when_state_is_waiting() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;

			given(stateEngine.getState(t)).willReturn(GameState.WAITING);
			given(waitingRoomService.countParticipants(t)).willReturn(5);

			// When
			WaitingRoomResponse response = sessionService.enterWaitingRoom(t, memberId);

			// Then
			assertThat(response.getMultigameId()).isEqualTo(t);
			assertThat(response.getState()).isEqualTo("WAITING");
			assertThat(response.getParticipation()).isEqualTo(5);
			verify(waitingRoomService).updateHeartbeat(t, memberId);
		}

		@Test
		@DisplayName("READY 상태이면 READY 상태를 반환한다")
		void it_returns_ready_state() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;

			given(stateEngine.getState(t)).willReturn(GameState.READY);
			given(waitingRoomService.countParticipants(t)).willReturn(10);

			// When
			WaitingRoomResponse response = sessionService.enterWaitingRoom(t, memberId);

			// Then
			assertThat(response.getState()).isEqualTo("READY");
		}

		@Test
		@DisplayName("PROGRESS 상태이면 PROGRESS 상태를 반환한다")
		void it_returns_progress_state() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;

			given(stateEngine.getState(t)).willReturn(GameState.PROGRESS);
			given(waitingRoomService.countParticipants(t)).willReturn(15);

			// When
			WaitingRoomResponse response = sessionService.enterWaitingRoom(t, memberId);

			// Then
			assertThat(response.getState()).isEqualTo("PROGRESS");
		}

		@Test
		@DisplayName("상태가 null이면 예외를 발생시킨다")
		void it_throws_exception_when_state_is_null() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;

			given(stateEngine.getState(t)).willReturn(null);

			// When & Then
			assertThatThrownBy(() -> sessionService.enterWaitingRoom(t, memberId))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
							.isEqualTo(ErrorCode.MULTIGAME_GAME_CANCELLED));
		}

		@Test
		@DisplayName("CANCELLED 상태이면 예외를 발생시킨다")
		void it_throws_exception_when_state_is_cancelled() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;

			given(stateEngine.getState(t)).willReturn(GameState.CANCELLED);

			// When & Then
			assertThatThrownBy(() -> sessionService.enterWaitingRoom(t, memberId))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
							.isEqualTo(ErrorCode.MULTIGAME_GAME_CANCELLED));
		}
	}

	@Nested
	@DisplayName("requestGame 메서드는")
	class Describe_requestGame {

		@Test
		@DisplayName("PROGRESS 상태이면 게임 요청을 처리한다")
		void it_processes_game_request_when_state_is_progress() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;
			int subjectId = 3;

			GameRequestResponse expectedResponse = GameRequestResponse.builder()
					.status("SUCCESS")
					.subjectId(subjectId)
					.remaining(2)
					.build();

			given(stateEngine.getState(t)).willReturn(GameState.PROGRESS);
			given(gameQueueService.processRequest(t, memberId, subjectId)).willReturn(expectedResponse);

			// When
			GameRequestResponse response = sessionService.requestGame(t, memberId, subjectId);

			// Then
			assertThat(response.getStatus()).isEqualTo("SUCCESS");
			assertThat(response.getSubjectId()).isEqualTo(subjectId);
		}

		@Test
		@DisplayName("WAITING 상태이면 WAITING 응답을 반환한다")
		void it_returns_waiting_response_when_state_is_waiting() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;
			int subjectId = 3;

			given(stateEngine.getState(t)).willReturn(GameState.WAITING);

			// When
			GameRequestResponse response = sessionService.requestGame(t, memberId, subjectId);

			// Then
			assertThat(response.getStatus()).isEqualTo("WAITING");
			assertThat(response.getCurrentState()).isEqualTo("WAITING");
		}

		@Test
		@DisplayName("READY 상태이면 WAITING 응답을 반환한다")
		void it_returns_waiting_response_when_state_is_ready() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;
			int subjectId = 3;

			given(stateEngine.getState(t)).willReturn(GameState.READY);

			// When
			GameRequestResponse response = sessionService.requestGame(t, memberId, subjectId);

			// Then
			assertThat(response.getStatus()).isEqualTo("WAITING");
			assertThat(response.getCurrentState()).isEqualTo("READY");
		}

		@Test
		@DisplayName("상태가 null이면 예외를 발생시킨다")
		void it_throws_exception_when_state_is_null() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;
			int subjectId = 3;

			given(stateEngine.getState(t)).willReturn(null);

			// When & Then
			assertThatThrownBy(() -> sessionService.requestGame(t, memberId, subjectId))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
							.isEqualTo(ErrorCode.MULTIGAME_GAME_CANCELLED));
		}

		@Test
		@DisplayName("CANCELLED 상태이면 예외를 발생시킨다")
		void it_throws_exception_when_state_is_cancelled() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;
			int subjectId = 3;

			given(stateEngine.getState(t)).willReturn(GameState.CANCELLED);

			// When & Then
			assertThatThrownBy(() -> sessionService.requestGame(t, memberId, subjectId))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
							.isEqualTo(ErrorCode.MULTIGAME_GAME_CANCELLED));
		}
	}
}
