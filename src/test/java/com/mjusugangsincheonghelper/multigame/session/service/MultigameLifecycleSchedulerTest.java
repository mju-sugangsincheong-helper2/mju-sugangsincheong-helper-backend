package com.mjusugangsincheonghelper.multigame.session.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import com.mjusugangsincheonghelper.multigame.session.domain.GameState;
import com.mjusugangsincheonghelper.multigame.session.domain.HeartbeatLedger;
import com.mjusugangsincheonghelper.multigame.session.domain.MultigameStateEngine;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameLifecycleScheduler 테스트")
class MultigameLifecycleSchedulerTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private AdvisoryLockService advisoryLockService;

	@Mock
	private SupplyEngineService supplyEngineService;

	@Mock
	private MultigameFinalizeService finalizeService;

	@Mock
	private MultigameReservationRepository reservationRepository;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private HashOperations<String, Object, Object> hashOperations;

	@Mock
	private MultigameStateEngine stateEngine;

	@Mock
	private HeartbeatLedger heartbeatLedger;

	private MultigameLifecycleScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new MultigameLifecycleScheduler(
				stringRedisTemplate,
				supplyEngineService,
				finalizeService,
				reservationRepository,
				null,
				stateEngine,
				heartbeatLedger
		);
	}

	@Nested
	@DisplayName("initializeGame 메서드는")
	class Describe_initializeGame {

		@Test
		@DisplayName("참여자 10명일 때 capacity=5, initialLimit=2로 설정한다")
		void it_initializes_with_correct_capacity_and_limit() {
			// Given
			String t = "20260726100000";
			int participantCount = 10;

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);

			// When
			scheduler.initializeGame(t, participantCount);

			// Then
			verify(stateEngine).setState(t, GameState.WAITING);
			verify(valueOperations).set(MultigameRedisKeyProvider.seq(t), "0");
			verify(valueOperations).set(MultigameRedisKeyProvider.admissionLimit(t), "2");
			verify(hashOperations).putAll(eq(MultigameRedisKeyProvider.seats(t)), any());
		}
	}

	@Nested
	@DisplayName("waitingJob 메서드는")
	class Describe_waitingJob {

		@BeforeEach
		void mockLock() {
			doAnswer(invocation -> {
				Runnable action = invocation.getArgument(2);
				action.run();
				return null;
			}).when(stateEngine).tryExecuteWithLock(anyString(), anyString(), any(Runnable.class));
		}

		@Test
		@DisplayName("게임 상태가 null이고 예약 정보가 있으면 개입하여 WAITING으로 초기화한다")
		void it_initializes_game_when_state_null_and_reservations_exist() {
			// Given
			given(stateEngine.getState(anyString())).willReturn(null);
			given(reservationRepository.findByStartTime(anyString()))
					.willReturn(List.of(MultigameReservationEntity.builder().build()));
			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);

			// When
			scheduler.waitingJob();

			// Then
			verify(stateEngine).setState(anyString(), eq(GameState.WAITING));
		}

		@Test
		@DisplayName("게임 상태가 null이고 예약 정보가 없으면 CANCELLED 처리한다")
		void it_cancels_game_when_state_null_and_no_reservations() {
			// Given
			given(stateEngine.getState(anyString())).willReturn(null);
			given(reservationRepository.findByStartTime(anyString())).willReturn(Collections.emptyList());

			// When
			scheduler.waitingJob();

			// Then
			verify(stateEngine).cancelGame(anyString());
		}

		@Test
		@DisplayName("게임 상태가 READY, PROGRESS, ENDED이면 CANCELLED 처리한다")
		void it_cancels_game_when_in_abnormal_state() {
			// Given
			given(stateEngine.getState(anyString())).willReturn(GameState.READY);

			// When
			scheduler.waitingJob();

			// Then
			verify(stateEngine).cancelGame(anyString());
		}
	}

	@Nested
	@DisplayName("readyJob 메서드는")
	class Describe_readyJob {

		@BeforeEach
		void mockLock() {
			doAnswer(invocation -> {
				Runnable action = invocation.getArgument(2);
				action.run();
				return null;
			}).when(stateEngine).tryExecuteWithLock(anyString(), anyString(), any(Runnable.class));
		}

		@Test
		@DisplayName("WAITING 상태에서 참가자가 2명 이상이면 READY 전이 및 스냅샷을 저장한다")
		void it_transitions_to_ready_when_participants_at_least_2() {
			// Given
			given(stateEngine.getState(anyString())).willReturn(GameState.WAITING);
			given(heartbeatLedger.countActiveHeartbeats(anyString())).willReturn(5);

			// When
			scheduler.readyJob();

			// Then
			verify(heartbeatLedger).saveParticipantSnapshot(anyString(), eq(5));
			verify(stateEngine).transitionTo(anyString(), eq(GameState.READY));
		}

		@Test
		@DisplayName("WAITING 상태에서 참가자가 2명 미만이면 CANCELLED 전이한다")
		void it_cancels_game_when_participants_less_than_2() {
			// Given
			given(stateEngine.getState(anyString())).willReturn(GameState.WAITING);
			given(heartbeatLedger.countActiveHeartbeats(anyString())).willReturn(1);

			// When
			scheduler.readyJob();

			// Then
			verify(stateEngine).cancelGame(anyString());
		}
	}

	@Nested
	@DisplayName("progressJob 메서드는")
	class Describe_progressJob {

		@BeforeEach
		void mockSessionLock() {
			doAnswer(invocation -> {
				Runnable action = invocation.getArgument(2);
				action.run();
				return null;
			}).when(stateEngine).tryExecuteWithSessionLock(anyString(), anyString(), any(Runnable.class));
		}

		@Test
		@DisplayName("READY 상태이면 PROGRESS 전이 및 SupplyEngineService를 실행한다")
		void it_transitions_to_progress_and_executes_supply_engine_when_ready() {
			// Given
			given(stateEngine.getState(anyString())).willReturn(GameState.READY);
			given(heartbeatLedger.getParticipantSnapshot(anyString())).willReturn(10);

			// When
			scheduler.progressJob();

			// Then
			verify(stateEngine).transitionTo(anyString(), eq(GameState.PROGRESS));
			verify(supplyEngineService).execute(anyString(), eq(10));
		}

		@Test
		@DisplayName("WAITING 상태이면 CANCELLED 처리한다")
		void it_cancels_game_when_in_waiting() {
			// Given
			given(stateEngine.getState(anyString())).willReturn(GameState.WAITING);

			// When
			scheduler.progressJob();

			// Then
			verify(stateEngine).cancelGame(anyString());
			verify(supplyEngineService, never()).execute(anyString(), anyInt());
		}
	}

	@Nested
	@DisplayName("endingJob 메서드는")
	class Describe_endingJob {

		@BeforeEach
		void mockLock() {
			doAnswer(invocation -> {
				Runnable action = invocation.getArgument(2);
				action.run();
				return null;
			}).when(stateEngine).tryExecuteWithLock(anyString(), anyString(), any(Runnable.class));
		}

		@Test
		@DisplayName("PROGRESS 상태이면 ENDED 전이 및 finalizeGame을 호출한다")
		void it_transitions_to_ended_and_finalizes_when_in_progress() {
			// Given
			given(stateEngine.getState(anyString())).willReturn(GameState.PROGRESS);

			// When
			scheduler.endingJob();

			// Then
			verify(stateEngine).transitionTo(anyString(), eq(GameState.ENDED));
			verify(finalizeService).finalizeGame(anyString());
		}

		@Test
		@DisplayName("WAITING 또는 READY 상태이면 CANCELLED 처리한다")
		void it_cancels_game_when_in_waiting_or_ready() {
			// Given
			given(stateEngine.getState(anyString())).willReturn(GameState.READY);

			// When
			scheduler.endingJob();

			// Then
			verify(stateEngine).cancelGame(anyString());
			verify(finalizeService, never()).finalizeGame(anyString());
		}
	}
}
