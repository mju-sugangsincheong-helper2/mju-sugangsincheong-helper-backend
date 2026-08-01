package com.mjusugangsincheonghelper.multigame.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.global.config.AdvisoryLockService.SessionLock;
import com.mjusugangsincheonghelper.multigame.game.config.MultigameProperties;
import com.mjusugangsincheonghelper.multigame.game.domain.RuntimeState;
import com.mjusugangsincheonghelper.multigame.game.runtime.GameRuntimeStore;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundSettlement;
import com.mjusugangsincheonghelper.multigame.result.service.RoundSettlementService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GameLifecycleScheduler 단위 테스트")
class GameLifecycleSchedulerTest {

	@Mock
	private AdvisoryLockService advisoryLockService;

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private GameRuntimeStore runtimeStore;

	@Mock
	private GameSupplyService supplyService;

	@Mock
	private RoundSettlementService settlementService;

	@Mock
	private MultigameProperties properties;

	private GameLifecycleScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new GameLifecycleScheduler(advisoryLockService, transactionTemplate, runtimeStore, supplyService, settlementService, properties);
		given(properties.getStartClose()).willReturn(java.time.LocalTime.of(2, 0));
		given(properties.getEndClose()).willReturn(java.time.LocalTime.of(5, 0));
		// TransactionTemplate.executeWithoutResult가 실제로 Consumer를 실행하도록 한다
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			Consumer<TransactionStatus> action = invocation.getArgument(0);
			action.accept(null);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());
		given(advisoryLockService.tryXactLock(anyString(), anyString())).willReturn(true);
	}

	private void withNow(LocalDateTime fixed, Runnable testBody) {
		try (MockedStatic<LocalDateTime> mocked = mockStatic(LocalDateTime.class)) {
			mocked.when(LocalDateTime::now).thenReturn(fixed);
			testBody.run();
		}
	}

	// ---------------------------------------------------------------------
	// ready(): T-5s 게임 확정
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("ready()는")
	class Describe_ready {

		@Test
		@DisplayName("대기 인원(W)이 2명 이상이면 READY로 확정하고 스냅샷을 저장한다")
		void it_readies_when_enough_waiting() {
			given(runtimeStore.removeExpiredHeartbeatsAndCount(any())).willReturn(5L);

			withNow(LocalDateTime.of(2026, 8, 1, 12, 9, 55), scheduler::ready);

			verify(runtimeStore).saveWaitingCount(5);
			verify(runtimeStore).setState(RuntimeState.READY);
		}

		@Test
		@DisplayName("대기 인원이 2명 미만이면 CANCELLED로 확정한다")
		void it_cancels_when_not_enough_waiting() {
			given(runtimeStore.removeExpiredHeartbeatsAndCount(any())).willReturn(1L);

			withNow(LocalDateTime.of(2026, 8, 1, 12, 9, 55), scheduler::ready);

			verify(runtimeStore).setState(RuntimeState.CANCELLED);
			verify(runtimeStore, never()).saveWaitingCount(any(Long.class));
		}

		@Test
		@DisplayName("Advisory Lock을 얻지 못하면 상태를 변경하지 않는다")
		void it_skips_when_lock_not_acquired() {
			given(advisoryLockService.tryXactLock(anyString(), anyString())).willReturn(false);
			given(runtimeStore.removeExpiredHeartbeatsAndCount(any())).willReturn(5L);

			withNow(LocalDateTime.of(2026, 8, 1, 12, 9, 55), scheduler::ready);

			verify(runtimeStore, never()).setState(any(RuntimeState.class));
			verify(runtimeStore, never()).saveWaitingCount(any(Long.class));
		}
	}

	// ---------------------------------------------------------------------
	// start(): T 게임 시작 + Supply Engine
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("start()는")
	class Describe_start {

		@Test
		@DisplayName("READY 상태면 좌석을 초기화하고 Supply Engine을 실행한다")
		void it_starts_when_ready() {
			given(advisoryLockService.trySessionLockHeld("multigame-progress", "20260801120000"))
					.willReturn(mock(SessionLock.class));
			given(runtimeStore.state()).willReturn(RuntimeState.READY);
			given(runtimeStore.waitingCount()).willReturn(10L);

			withNow(LocalDateTime.of(2026, 8, 1, 12, 0, 0), scheduler::start);

			// capacity = max(1, round(10 / 2)) = 5
			verify(runtimeStore).startProgress(5);
			verify(supplyService).run();
			verify(runtimeStore, never()).setState(RuntimeState.CANCELLED);
		}

		@Test
		@DisplayName("READY가 아니면 CANCELLED로 표시하고 Supply Engine을 실행하지 않는다")
		void it_cancels_when_state_not_ready() {
			given(advisoryLockService.trySessionLockHeld("multigame-progress", "20260801120000"))
					.willReturn(mock(SessionLock.class));
			given(runtimeStore.state()).willReturn(RuntimeState.CANCELLED);

			withNow(LocalDateTime.of(2026, 8, 1, 12, 0, 0), scheduler::start);

			verify(runtimeStore).setState(RuntimeState.CANCELLED);
			verify(runtimeStore, never()).startProgress(any(Integer.class));
			verify(supplyService, never()).run();
		}

		@Test
		@DisplayName("Session Lock을 얻지 못하면 아무것도 하지 않는다")
		void it_skips_when_session_lock_not_acquired() {
			given(advisoryLockService.trySessionLockHeld(anyString(), anyString())).willReturn(null);

			withNow(LocalDateTime.of(2026, 8, 1, 12, 0, 0), scheduler::start);

			verify(runtimeStore, never()).startProgress(any(Integer.class));
			verify(supplyService, never()).run();
		}
	}

	// ---------------------------------------------------------------------
	// finish(): T+30s 정산
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("finish()는")
	class Describe_finish {

		@Test
		@DisplayName("PROGRESS 상태면 정산 결과를 영속화하고 Redis를 초기화한다")
		void it_settles_and_clears() {
			given(runtimeStore.state()).willReturn(RuntimeState.PROGRESS);
			given(runtimeStore.participants()).willReturn(5L);
			given(runtimeStore.waitingCount()).willReturn(10L);
			given(runtimeStore.eventLog()).willReturn(List.of("1:ENQUEUED:1:1:1:0", "1:SUCCESS:1:2:1:1"));

			withNow(LocalDateTime.of(2026, 8, 1, 12, 0, 30), scheduler::finish);

			ArgumentCaptor<RoundSettlement> settlementCaptor = ArgumentCaptor.forClass(RoundSettlement.class);
			verify(settlementService).save(settlementCaptor.capture());
			RoundSettlement settlement = settlementCaptor.getValue();
			assertThat(settlement.startTime()).isEqualTo("20260801120000");
			assertThat(settlement.participantCount()).isEqualTo(5);
			// capacity = max(1, round(10 / 2)) = 5
			assertThat(settlement.capacity()).isEqualTo(5);
			assertThat(settlement.events()).hasSize(2);

			verify(runtimeStore).clear();
		}

		@Test
		@DisplayName("PROGRESS가 아니면 정산하지 않는다")
		void it_skips_when_not_progress() {
			given(runtimeStore.state()).willReturn(RuntimeState.CANCELLED);

			withNow(LocalDateTime.of(2026, 8, 1, 12, 0, 30), scheduler::finish);

			verify(settlementService, never()).save(any(RoundSettlement.class));
			verify(runtimeStore, never()).clear();
		}
	}

	// ---------------------------------------------------------------------
	// 미운영 시간대 (CLOSED): 새벽 2시 ~ 5시
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("미운영 시간대(02:00 ~ 05:00)에는")
	class Describe_closed {

		@Test
		@DisplayName("ready()가 아무것도 하지 않는다")
		void ready_is_noop() {
			// CLOSED 판정은 properties 기반 (기본 02:00 ~ 05:00)
			withNow(LocalDateTime.of(2026, 8, 1, 3, 0, 0), scheduler::ready);

			verify(runtimeStore, never()).removeExpiredHeartbeatsAndCount(any());
			verify(runtimeStore, never()).setState(any(RuntimeState.class));
		}

		@Test
		@DisplayName("start()가 아무것도 하지 않는다")
		void start_is_noop() {
			withNow(LocalDateTime.of(2026, 8, 1, 3, 0, 0), scheduler::start);

			verify(advisoryLockService, never()).trySessionLockHeld(anyString(), anyString());
		}

		@Test
		@DisplayName("finish()가 아무것도 하지 않는다")
		void finish_is_noop() {
			withNow(LocalDateTime.of(2026, 8, 1, 3, 0, 0), scheduler::finish);

			verify(settlementService, never()).save(any(RoundSettlement.class));
			verify(runtimeStore, never()).clear();
		}
	}
}
