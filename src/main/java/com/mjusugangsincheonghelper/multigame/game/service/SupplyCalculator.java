package com.mjusugangsincheonghelper.multigame.game.service;

public final class SupplyCalculator {

	private SupplyCalculator() {
	}

	public static long initialLimit(long waitingCount) {
		return Math.max(1, waitingCount / 5);
	}

	public static long nextLimit(long currentLimit, long participants, long queueLength, int remainingSeconds) {
		if (queueLength == 0 || participants <= currentLimit) {
			return Math.min(currentLimit, participants);
		}
		int divisor = remainingSeconds <= 4 ? remainingSeconds : 4;
		long supply = (queueLength + divisor - 1) / divisor;
		return Math.min(participants, currentLimit + supply);
	}
}
