package com.mjusugangsincheonghelper.multigame.game.service;

import com.mjusugangsincheonghelper.multigame.game.runtime.GameRuntimeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameSupplyService {

	private static final int DURATION_SECONDS = 30;

	private final GameRuntimeStore runtimeStore;

	public void run() {
		long limit = SupplyCalculator.initialLimit(runtimeStore.waitingCount());
		runtimeStore.setAdmissionLimit(limit);
		for (int elapsed = 1; elapsed < DURATION_SECONDS; elapsed++) {
			if (!sleepOneSecond()) {
				return;
			}
			limit = SupplyCalculator.nextLimit(limit, runtimeStore.participants(), runtimeStore.queueLength(), DURATION_SECONDS - elapsed);
			runtimeStore.setAdmissionLimit(limit);
		}
	}

	private boolean sleepOneSecond() {
		try {
			Thread.sleep(1_000);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
