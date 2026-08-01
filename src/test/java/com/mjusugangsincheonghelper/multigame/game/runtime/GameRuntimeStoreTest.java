package com.mjusugangsincheonghelper.multigame.game.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.multigame.game.domain.RuntimeState;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GameRuntimeStore Redis 연산 단위 테스트")
class GameRuntimeStoreTest {

	private static final String STATE = "multigame:round:state:control";
	private static final String HEARTBEAT = "multigame:round:heartbeat:ledger";
	private static final String WAITING_COUNT = "multigame:round:waiting_count:cache";
	private static final String PARTICIPANTS = "multigame:round:participants:ledger";
	private static final String QUEUE = "multigame:round:queue:ledger";
	private static final String SEQUENCE = "multigame:round:seq:ledger";
	private static final String LIMIT = "multigame:round:limit:control";
	private static final String SEATS = "multigame:round:seats:ledger";
	private static final String SUCCESS_MEMBERS = "multigame:round:success_members:ledger";
	private static final String EVENT_LOG = "multigame:round:event_log:stream";

	@Mock
	private StringRedisTemplate redis;

	@Mock
	private ValueOperations<String, String> valueOps;

	@Mock
	private ZSetOperations<String, String> zSetOps;

	@Mock
	private SetOperations<String, String> setOps;

	@Mock
	private HashOperations<String, Object, Object> hashOps;

	@Mock
	private ListOperations<String, String> listOps;

	private GameRuntimeStore store;

	@BeforeEach
	void setUp() {
		store = new GameRuntimeStore(redis);
		given(redis.opsForValue()).willReturn(valueOps);
		given(redis.opsForZSet()).willReturn(zSetOps);
		given(redis.opsForSet()).willReturn(setOps);
		given(redis.opsForHash()).willReturn(hashOps);
		given(redis.opsForList()).willReturn(listOps);
	}

	// ---------------------------------------------------------------------
	// state / setState
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("state()/setState()는")
	class Describe_state {

		@Test
		@DisplayName("Redis 값에서 RuntimeState로 변환한다")
		void readsStateFromRedis() {
			given(valueOps.get(STATE)).willReturn("READY");
			assertThat(store.state()).isEqualTo(RuntimeState.READY);

			given(valueOps.get(STATE)).willReturn("PROGRESS");
			assertThat(store.state()).isEqualTo(RuntimeState.PROGRESS);
		}

		@Test
		@DisplayName("키가 없거나 알 수 없는 값이면 null을 반환한다")
		void returnsNullWhenMissingOrUnknown() {
			given(valueOps.get(STATE)).willReturn(null);
			assertThat(store.state()).isNull();

			given(valueOps.get(STATE)).willReturn("BOGUS");
			assertThat(store.state()).isNull();
		}

		@Test
		@DisplayName("상태 이름을 그대로 Redis에 저장한다")
		void writesStateName() {
			store.setState(RuntimeState.CANCELLED);
			verify(valueOps).set(STATE, "CANCELLED");
		}
	}

	// ---------------------------------------------------------------------
	// heartbeat / 대기 인원 집계
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("heartbeat 계열은")
	class Describe_heartbeat {

		@Test
		@DisplayName("recordHeartbeat는 ZSET에 현재 타임스탬프를 점수로 추가한다")
		void recordsHeartbeatWithTimestampScore() {
			Instant now = Instant.ofEpochMilli(1_000_005);
			store.recordHeartbeat(7L, now);
			verify(zSetOps).add(HEARTBEAT, "7", 1_000_005L);
		}

		@Test
		@DisplayName("removeExpiredHeartbeatsAndCount는 3초 이전 데이터를 제거하고 카운트한다")
		void removesExpiredAndCounts() {
			given(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).willReturn(4L);

			long count = store.removeExpiredHeartbeatsAndCount(Instant.ofEpochMilli(1_000_005));

			assertThat(count).isEqualTo(4);
			ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
			verify(redis).execute(any(DefaultRedisScript.class), anyList(), argsCaptor.capture());
			// now - 3초 경계값이 ARGV로 전달된다
			assertThat(argsCaptor.getValue()).containsExactly("997005");
		}
	}

	// ---------------------------------------------------------------------
	// 대기 인원 스냅샷
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("waitingCount 계열은")
	class Describe_waitingCount {

		@Test
		@DisplayName("saveWaitingCount는 W 스냅샷을 저장한다")
		void savesWaitingCount() {
			store.saveWaitingCount(23);
			verify(valueOps).set(WAITING_COUNT, "23");
		}

		@Test
		@DisplayName("waitingCount는 저장된 스냅샷을 읽는다")
		void readsWaitingCount() {
			given(valueOps.get(WAITING_COUNT)).willReturn("23");
			assertThat(store.waitingCount()).isEqualTo(23);
		}

		@Test
		@DisplayName("스냅샷이 없으면 0을 반환한다")
		void returnsZeroWhenMissing() {
			given(valueOps.get(WAITING_COUNT)).willReturn(null);
			assertThat(store.waitingCount()).isZero();
		}
	}

	// ---------------------------------------------------------------------
	// 게임 시작 초기화
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("startProgress()는")
	class Describe_startProgress {

		@Test
		@DisplayName("이전 게임 데이터 키만 지우고 좌석 1~6을 초기화한 뒤 PROGRESS로 전환한다 (heartbeat/waiting_count는 보존)")
		void initializesSeatsAndProgressState() {
			store.startProgress(5);

			ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
			verify(redis).delete(keysCaptor.capture());
			// 문서(Layer 1 progressCron)와 정합: heartbeat와 waiting_count는 이 시점에 지우지 않는다.
			assertThat(keysCaptor.getValue()).containsExactlyInAnyOrder(
					PARTICIPANTS, QUEUE, SEQUENCE, LIMIT, SEATS, SUCCESS_MEMBERS, EVENT_LOG);

			verify(valueOps).set(SEQUENCE, "0");
			verify(valueOps).set(LIMIT, "0");
			verify(hashOps).putAll(SEATS, Map.of(
					"1", "5", "2", "5", "3", "5", "4", "5", "5", "5", "6", "5"));
			verify(valueOps).set(STATE, "PROGRESS");
		}
	}

	// ---------------------------------------------------------------------
	// 진입/이탈/P
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("진입/이탈 계열은")
	class Describe_enterLeave {

		@Test
		@DisplayName("enter는 Set에 추가하고 현재 참여자 수를 반환한다")
		void entersAndReturnsCount() {
			given(setOps.add(PARTICIPANTS, "7")).willReturn(1L);
			given(setOps.size(PARTICIPANTS)).willReturn(3L);

			assertThat(store.enter(7)).isEqualTo(3);
			verify(setOps).add(PARTICIPANTS, "7");
		}

		@Test
		@DisplayName("leave는 참여자 집합과 대기열에서 함께 제거한다")
		void removesFromParticipantsAndQueue() {
			store.leave(7);
			verify(setOps).remove(PARTICIPANTS, "7");
			verify(zSetOps).remove(QUEUE, "7");
		}

		@Test
		@DisplayName("participants()는 현재 참여자 수를 반환한다")
		void countsParticipants() {
			given(setOps.size(PARTICIPANTS)).willReturn(12L);
			assertThat(store.participants()).isEqualTo(12);
		}

		@Test
		@DisplayName("hasEntered는 진입 여부를 반환한다")
		void checksEntered() {
			given(setOps.isMember(PARTICIPANTS, "7")).willReturn(Boolean.TRUE);
			assertThat(store.hasEntered(7)).isTrue();

			given(setOps.isMember(PARTICIPANTS, "8")).willReturn(Boolean.FALSE);
			assertThat(store.hasEntered(8)).isFalse();
		}
	}

	// ---------------------------------------------------------------------
	// 대기열 / 허용선 / 이벤트 로그 / 정리
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("대기열·허용선·로그 계열은")
	class Describe_queueAndLog {

		@Test
		@DisplayName("queueLength는 대기열 길이를 반환한다")
		void queueLength() {
			given(zSetOps.size(QUEUE)).willReturn(80L);
			assertThat(store.queueLength()).isEqualTo(80);
		}

		@Test
		@DisplayName("setAdmissionLimit은 허용선을 갱신한다")
		void setsAdmissionLimit() {
			store.setAdmissionLimit(40);
			verify(valueOps).set(LIMIT, "40");
		}

		@Test
		@DisplayName("eventLog는 전체 이벤트 로그를 반환한다")
		void readsEventLog() {
			List<String> logs = List.of("1:ENQUEUED:1:1:1:0", "1:SUCCESS:1:2:1:1");
			given(listOps.range(EVENT_LOG, 0, -1)).willReturn(logs);

			assertThat(store.eventLog()).containsExactlyElementsOf(logs);
		}
	}

	@Nested
	@DisplayName("clear()는")
	class Describe_clear {

		@Test
		@DisplayName("모든 전역 게임 키를 삭제한다")
		void deletesAllGlobalKeys() {
			store.clear();

			ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
			verify(redis).delete(keysCaptor.capture());
			assertThat(keysCaptor.getValue()).containsExactlyInAnyOrder(
					STATE, HEARTBEAT, WAITING_COUNT, PARTICIPANTS, QUEUE,
					SEQUENCE, LIMIT, SEATS, SUCCESS_MEMBERS, EVENT_LOG);
		}
	}

	@Nested
	@DisplayName("applyKeys()는")
	class Describe_applyKeys {

		@Test
		@DisplayName("Lua 스크립트 KEYS 순서와 일치하는 키 목록을 반환한다")
		void matchesLuaKeyOrder() {
			assertThat(store.applyKeys()).containsExactly(
					STATE, QUEUE, SEQUENCE, LIMIT, SEATS, SUCCESS_MEMBERS, EVENT_LOG);
		}
	}
}
