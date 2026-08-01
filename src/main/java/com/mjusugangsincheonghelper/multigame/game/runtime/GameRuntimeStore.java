package com.mjusugangsincheonghelper.multigame.game.runtime;

import com.mjusugangsincheonghelper.multigame.game.domain.RuntimeState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameRuntimeStore {

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

	private static final List<String> ALL_KEYS = List.of(
			STATE, HEARTBEAT, WAITING_COUNT, PARTICIPANTS, QUEUE, SEQUENCE, LIMIT, SEATS, SUCCESS_MEMBERS, EVENT_LOG
	);

	private static final DefaultRedisScript<Long> ACTIVE_WAITING_COUNT = new DefaultRedisScript<>("""
			redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
			return redis.call('ZCARD', KEYS[1])
			""", Long.class);

	private final StringRedisTemplate redis;

	public RuntimeState state() {
		return RuntimeState.from(redis.opsForValue().get(STATE));
	}

	public void setState(RuntimeState state) {
		redis.opsForValue().set(STATE, state.name());
	}

	public void recordHeartbeat(long memberId, Instant now) {
		redis.opsForZSet().add(HEARTBEAT, String.valueOf(memberId), now.toEpochMilli());
	}

	public long removeExpiredHeartbeatsAndCount(Instant now) {
		Long count = redis.execute(ACTIVE_WAITING_COUNT, List.of(HEARTBEAT), String.valueOf(now.minusSeconds(4).toEpochMilli()));
		return count == null ? 0 : count;
	}

	public void saveWaitingCount(long waitingCount) {
		redis.opsForValue().set(WAITING_COUNT, String.valueOf(waitingCount));
	}

	public long waitingCount() {
		return number(redis.opsForValue().get(WAITING_COUNT));
	}

	public void startProgress(int capacity) {
		// [핵심 초기화] 문서(Layer 1 progressCron)와 정합: 게임 데이터 키(participants, queue, seq, limit, seats,
		// success_members, event_log)만 DEL로 초기화한다.
		// HEARTBEAT와 WAITING_COUNT는 이 시점에 지우지 않는다 — waiting_count(W)는 endingCron(T+30s)이
		// capacity(round(W/2)) 영속화에 여전히 읽어야 하며, heartbeat는 endingCron의 전역 키 정리(clear())에서 제거된다.
		// WAITING_COUNT는 다음 라운드 readyCron이 다시 덮어쓰므로 별도 정리가 필요 없다.
		redis.delete(List.of(PARTICIPANTS, QUEUE, SEQUENCE, LIMIT, SEATS, SUCCESS_MEMBERS, EVENT_LOG));
		redis.opsForValue().set(SEQUENCE, "0");
		redis.opsForValue().set(LIMIT, "0");
		Map<String, String> seats = Map.of("1", String.valueOf(capacity), "2", String.valueOf(capacity),
				"3", String.valueOf(capacity), "4", String.valueOf(capacity), "5", String.valueOf(capacity), "6", String.valueOf(capacity));
		redis.opsForHash().putAll(SEATS, seats);
		setState(RuntimeState.PROGRESS);
	}

	public long enter(long memberId) {
		redis.opsForSet().add(PARTICIPANTS, String.valueOf(memberId));
		Long count = redis.opsForSet().size(PARTICIPANTS);
		return count == null ? 0 : count;
	}

	public void leave(long memberId) {
		String member = String.valueOf(memberId);
		redis.opsForSet().remove(PARTICIPANTS, member);
		// 대기열 키는 (유저, 과목) 단위이므로, 해당 유저의 모든 과목별 대기 항목을 제거한다
		redis.opsForZSet().remove(QUEUE, member + ":1", member + ":2", member + ":3",
				member + ":4", member + ":5", member + ":6");
	}

	public long participants() {
		Long count = redis.opsForSet().size(PARTICIPANTS);
		return count == null ? 0 : count;
	}

	public boolean hasEntered(long memberId) {
		Boolean entered = redis.opsForSet().isMember(PARTICIPANTS, String.valueOf(memberId));
		return Boolean.TRUE.equals(entered);
	}

	public long queueLength() {
		Long count = redis.opsForZSet().size(QUEUE);
		return count == null ? 0 : count;
	}

	public void setAdmissionLimit(long limit) {
		redis.opsForValue().set(LIMIT, String.valueOf(limit));
	}

	public List<String> eventLog() {
		return redis.opsForList().range(EVENT_LOG, 0, -1);
	}

	public void clear() {
		redis.delete(ALL_KEYS);
	}

	public List<String> applyKeys() {
		return List.of(STATE, QUEUE, SEQUENCE, LIMIT, SEATS, SUCCESS_MEMBERS, EVENT_LOG);
	}

	private long number(String value) {
		return value == null ? 0 : Long.parseLong(value);
	}
}
