package com.mjusugangsincheonghelper.multigame.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.game.domain.GameStatus;
import com.mjusugangsincheonghelper.multigame.game.domain.GameStatusResolver;
import com.mjusugangsincheonghelper.multigame.game.dto.GameApplyResponse;
import com.mjusugangsincheonghelper.multigame.game.dto.GameEnterResponse;
import com.mjusugangsincheonghelper.multigame.game.dto.GameWaitingResponse;
import com.mjusugangsincheonghelper.multigame.game.runtime.GameApplyScript;
import com.mjusugangsincheonghelper.multigame.game.runtime.GameRuntimeStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GameSessionService 단위 테스트")
class GameSessionServiceTest {

	private static final List<String> APPLY_KEYS = List.of(
			"multigame:round:state:control",
			"multigame:round:queue:ledger",
			"multigame:round:seq:ledger",
			"multigame:round:limit:control",
			"multigame:round:seats:ledger",
			"multigame:round:success_members:ledger",
			"multigame:round:event_log:stream");

	@Mock
	private GameRuntimeStore runtimeStore;

	@Mock
	private StringRedisTemplate redis;

	@Mock
	private GameStatusResolver statusResolver;

	private GameSessionService service;

	@BeforeEach
	void setUp() {
		service = new GameSessionService(runtimeStore, redis, statusResolver);
	}

	private void resolves(GameStatus status) {
		given(statusResolver.resolve(any(), isNull())).willReturn(status);
	}

	// ---------------------------------------------------------------------
	// waitingRoom
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("waitingRoom 메서드는")
	class Describe_waitingRoom {

		@Test
		@DisplayName("WAITING 상태에서 heartbeat를 갱신하고 현재 대기 인원을 반환한다")
		void it_refreshes_heartbeat_and_counts_waiting() {
			resolves(GameStatus.WAITING);
			given(runtimeStore.removeExpiredHeartbeatsAndCount(any(Instant.class))).willReturn(23L);

			GameWaitingResponse response = service.waitingRoom(7L);

			assertThat(response.getState()).isEqualTo("WAITING");
			assertThat(response.getParticipation()).isEqualTo(23);
			assertThat(response.getMultigameId()).matches("\\d{14}");
			verify(runtimeStore).recordHeartbeat(eq(7L), any(Instant.class));
		}

		@Test
		@DisplayName("READY 상태에서 heartbeat를 갱신하고 readyCron 스냅샷 인원을 반환한다")
		void it_refreshes_heartbeat_and_uses_waiting_count_snapshot() {
			resolves(GameStatus.READY);
			given(runtimeStore.waitingCount()).willReturn(30L);

			GameWaitingResponse response = service.waitingRoom(7L);

			assertThat(response.getState()).isEqualTo("READY");
			assertThat(response.getParticipation()).isEqualTo(30);
			verify(runtimeStore).recordHeartbeat(eq(7L), any(Instant.class));
			verify(runtimeStore, never()).removeExpiredHeartbeatsAndCount(any(Instant.class));
		}

		@Test
		@DisplayName("PROGRESS 상태에서 heartbeat는 갱신하지 않고 참여자 수를 반환한다")
		void it_returns_progress_participants_without_heartbeat() {
			resolves(GameStatus.PROGRESS);
			given(runtimeStore.participants()).willReturn(15L);

			GameWaitingResponse response = service.waitingRoom(7L);

			assertThat(response.getState()).isEqualTo("PROGRESS");
			assertThat(response.getParticipation()).isEqualTo(15);
			verify(runtimeStore, never()).recordHeartbeat(anyLong(), any(Instant.class));
		}

		@Test
		@DisplayName("CLOSED 상태에서 참여 인원 0과 heartbeat 미갱신을 반환한다")
		void it_returns_closed_without_heartbeat() {
			resolves(GameStatus.CLOSED);

			GameWaitingResponse response = service.waitingRoom(7L);

			assertThat(response.getState()).isEqualTo("CLOSED");
			assertThat(response.getParticipation()).isZero();
			verify(runtimeStore, never()).recordHeartbeat(anyLong(), any(Instant.class));
		}

		@Test
		@DisplayName("STARTING 상태에서 Redis를 재조회하고 참여 인원 0을 반환한다")
		void it_rechecks_redis_when_starting() {
			given(statusResolver.resolve(any(), isNull())).willReturn(GameStatus.STARTING);
			given(statusResolver.resolve(any(), any())).willReturn(GameStatus.STARTING);

			GameWaitingResponse response = service.waitingRoom(7L);

			assertThat(response.getState()).isEqualTo("STARTING");
			assertThat(response.getParticipation()).isZero();
			verify(statusResolver, times(2)).resolve(any(), any());
			verify(runtimeStore, never()).recordHeartbeat(anyLong(), any(Instant.class));
		}
	}

	// ---------------------------------------------------------------------
	// enter
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("enter 메서드는")
	class Describe_enter {

		@Test
		@DisplayName("PROGRESS 상태에서 진입을 허가하고 참여자 수를 반환한다")
		void it_enters_when_progress() {
			resolves(GameStatus.PROGRESS);
			given(runtimeStore.enter(7L)).willReturn(9L);

			GameEnterResponse response = service.enter(7L);

			assertThat(response.getState()).isEqualTo("PROGRESS");
			assertThat(response.getParticipation()).isEqualTo(9);
			verify(runtimeStore).enter(7L);
		}

		@Test
		@DisplayName("취소된 게임이면 MULTIGAME_GAME_CANCELLED를 던진다")
		void it_throws_when_cancelled() {
			resolves(GameStatus.CANCELLED);

			assertThatThrownBy(() -> service.enter(7L))
					.isInstanceOf(BaseException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MULTIGAME_GAME_CANCELLED);
		}

		@Test
		@DisplayName("진행 중이 아니면 MULTIGAME_GAME_INVALID_STATE를 던진다")
		void it_throws_when_not_progress() {
			resolves(GameStatus.WAITING);

			assertThatThrownBy(() -> service.enter(7L))
					.isInstanceOf(BaseException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MULTIGAME_GAME_INVALID_STATE);
		}
	}

	// ---------------------------------------------------------------------
	// leave
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("leave 메서드는")
	class Describe_leave {

		@Test
		@DisplayName("참여자 집합과 대기열에서 제거한다")
		void it_leaves() {
			service.leave(7L);
			verify(runtimeStore).leave(7L);
		}
	}

	// ---------------------------------------------------------------------
	// apply
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("apply 메서드는")
	class Describe_apply {

		@BeforeEach
		void stubEntered() {
			given(runtimeStore.hasEntered(7L)).willReturn(true);
			given(runtimeStore.applyKeys()).willReturn(APPLY_KEYS);
		}

		@Test
		@DisplayName("PROGRESS가 아니면 Lua를 실행하지 않고 BLOCKED를 반환한다")
		void it_blocks_when_not_progress() {
			resolves(GameStatus.ENDED);

			GameApplyResponse response = service.apply(7L, 1);

			assertThat(response.getStatus()).isEqualTo("BLOCKED");
			assertThat(response.getCurrentState()).isEqualTo("ENDED");
			verify(redis, never()).execute(any(), anyList(), any(Object[].class));
		}

		@Test
		@DisplayName("미진입 유저는 MULTIGAME_GAME_INVALID_STATE로 거부한다")
		void it_rejects_unentered_member() {
			resolves(GameStatus.PROGRESS);
			given(runtimeStore.hasEntered(7L)).willReturn(false);

			assertThatThrownBy(() -> service.apply(7L, 1))
					.isInstanceOf(BaseException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MULTIGAME_GAME_INVALID_STATE);
			verify(redis, never()).execute(any(), anyList(), any(Object[].class));
		}

		@Test
		@DisplayName("SUCCESS 결과를 subjectId와 잔여 좌석으로 매핑한다")
		void it_maps_success() {
			resolves(GameStatus.PROGRESS);
			given(redis.execute(any(), anyList(), any(Object[].class))).willReturn(List.of("SUCCESS", "2", 3));

			GameApplyResponse response = service.apply(7L, 2);

			assertThat(response.getStatus()).isEqualTo("SUCCESS");
			assertThat(response.getSubjectId()).isEqualTo(2);
			assertThat(response.getRemaining()).isEqualTo(3);
		}

		@Test
		@DisplayName("PENDING 결과를 seq, limit, rank(앞 대기 수)로 매핑한다")
		void it_maps_pending() {
			resolves(GameStatus.PROGRESS);
			given(redis.execute(any(), anyList(), any(Object[].class))).willReturn(List.of("PENDING", "5", "10", "2"));

			GameApplyResponse response = service.apply(7L, 1);

			assertThat(response.getStatus()).isEqualTo("PENDING");
			assertThat(response.getSeq()).isEqualTo(5);
			assertThat(response.getLimit()).isEqualTo(10);
			assertThat(response.getRank()).isEqualTo(2);
		}

		@Test
		@DisplayName("FAIL_SOLDOUT 결과를 subjectId로 매핑한다")
		void it_maps_fail_soldout() {
			resolves(GameStatus.PROGRESS);
			given(redis.execute(any(), anyList(), any(Object[].class))).willReturn(List.of("FAIL_SOLDOUT", "4"));

			GameApplyResponse response = service.apply(7L, 4);

			assertThat(response.getStatus()).isEqualTo("FAIL_SOLDOUT");
			assertThat(response.getSubjectId()).isEqualTo(4);
		}

		@Test
		@DisplayName("FAIL_DUPLICATE 결과를 subjectId로 매핑한다")
		void it_maps_fail_duplicate() {
			resolves(GameStatus.PROGRESS);
			given(redis.execute(any(), anyList(), any(Object[].class))).willReturn(List.of("FAIL_DUPLICATE", "3"));

			GameApplyResponse response = service.apply(7L, 3);

			assertThat(response.getStatus()).isEqualTo("FAIL_DUPLICATE");
			assertThat(response.getSubjectId()).isEqualTo(3);
		}

		@Test
		@DisplayName("Lua가 null을 반환하면 MULTIGAME_LUA_SCRIPT_ERROR를 던진다")
		void it_throws_when_lua_returns_null() {
			resolves(GameStatus.PROGRESS);
			given(redis.execute(any(), anyList(), any(Object[].class))).willReturn(null);

			assertThatThrownBy(() -> service.apply(7L, 1))
					.isInstanceOf(BaseException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR);
		}

		@Test
		@DisplayName("Lua가 빈 결과를 반환하면 MULTIGAME_LUA_SCRIPT_ERROR를 던진다")
		void it_throws_when_lua_returns_empty() {
			resolves(GameStatus.PROGRESS);
			given(redis.execute(any(), anyList(), any(Object[].class))).willReturn(List.of());

			assertThatThrownBy(() -> service.apply(7L, 1))
					.isInstanceOf(BaseException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR);
		}

		@Test
		@DisplayName("Lua 실행 중 예외는 MULTIGAME_LUA_SCRIPT_ERROR로 감싼다")
		void it_wraps_lua_exception() {
			resolves(GameStatus.PROGRESS);
			given(redis.execute(any(), anyList(), any(Object[].class))).willThrow(new IllegalStateException("boom"));

			assertThatThrownBy(() -> service.apply(7L, 1))
					.isInstanceOf(BaseException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR);
		}

		@Test
		@DisplayName("Lua 스크립트와 전역 키를 함께 실행한다")
		void it_executes_script_with_keys() {
			resolves(GameStatus.PROGRESS);
			given(redis.execute(any(), anyList(), any(Object[].class))).willReturn(List.of("SUCCESS", "1", 1));

			service.apply(7L, 1);

			verify(redis).execute(eq(GameApplyScript.SCRIPT), eq(APPLY_KEYS), any(Object[].class));
		}
	}
}
