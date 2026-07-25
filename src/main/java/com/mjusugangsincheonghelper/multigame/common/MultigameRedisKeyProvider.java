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
}
