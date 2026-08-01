package com.mjusugangsincheonghelper.multigame.game.service;

public final class SupplyCalculator {

	private SupplyCalculator() {
	}

	public static long initialLimit(long waitingCount) {
		return Math.max(1, waitingCount / 5);
	}

	public static long nextLimit(long currentLimit, long participants, long queueLength, int remainingSeconds) {
		// 과목별 신청이 가능하므로 허용선 상한은 (유저 수 × 최대 과목 수 6)회의 총 시도 수다.
		long attemptCeiling = 6 * participants;
		if (queueLength == 0 || attemptCeiling <= currentLimit) {
			return Math.min(currentLimit, attemptCeiling);
		}
		int divisor = remainingSeconds <= 4 ? remainingSeconds : 4;
		long supply = (queueLength + divisor - 1) / divisor;
		return Math.min(attemptCeiling, currentLimit + supply);
	}
}
