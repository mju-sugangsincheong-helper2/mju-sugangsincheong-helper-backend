package com.mjusugangsincheonghelper.multigame.common;

public final class MultigameRedisKeyProvider {

	private MultigameRedisKeyProvider() {
	}

	private static final String PREFIX = "multigame::";

	public static String state(String t) {
		return PREFIX + t + "::state::control";
	}

	public static String heartbeat(String t, Long userId) {
		return PREFIX + t + "::heartbeat::" + userId + "::session";
	}

	public static String heartbeatPattern(String t) {
		return PREFIX + t + "::heartbeat::*::session";
	}

	public static String queue(String t) {
		return PREFIX + t + "::queue::ledger";
	}

	public static String seq(String t) {
		return PREFIX + t + "::seq::ledger";
	}

	public static String admissionLimit(String t) {
		return PREFIX + t + "::admission_limit::control";
	}

	public static String seats(String t) {
		return PREFIX + t + "::seats::ledger";
	}

	public static String history(String t) {
		return PREFIX + t + "::history::ledger";
	}

	public static String successMembers(String t) {
		return PREFIX + t + "::success_members::ledger";
	}

	/**
	 * ReadyJob 시점의 확정된 참여자 수 (절댓값)
	 * heartbeat TTL 문제로 이후 시점에서는 정확한 카운트가 불가능하므로,
	 * ReadyJob에서 스냅샷으로 저장하여 ProgressJob, FinalizeService가 참조합니다.
	 */
	public static String participantCount(String t) {
		return PREFIX + t + "::participant_count::snapshot";
	}

	/**
	 * 게임 진행에 필요한 Redis 키들을 WAITING 상태로 세팅합니다.
	 */
	public static void initializeGameSession(
			org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate,
			com.mjusugangsincheonghelper.multigame.session.domain.MultigameStateEngine stateEngine,
			String multigameId,
			int participantCount) {
		int capacity = Math.max(1, participantCount / 2);
		int initialLimit = Math.max(1, (int) Math.floor(participantCount * 0.2));

		java.util.Map<String, String> seats = new java.util.HashMap<>();
		for (int i = 1; i <= 6; i++) {
			seats.put(String.valueOf(i), String.valueOf(capacity));
		}

		stateEngine.setState(multigameId, com.mjusugangsincheonghelper.multigame.session.domain.GameState.WAITING);
		stringRedisTemplate.opsForValue().set(seq(multigameId), "0");
		stringRedisTemplate.opsForValue().set(admissionLimit(multigameId), String.valueOf(initialLimit));
		stringRedisTemplate.opsForHash().putAll(seats(multigameId), seats);
	}
}
