package com.mjusugangsincheonghelper.multigame.game.service;

import com.mjusugangsincheonghelper.multigame.game.config.MultigameProperties;
import com.mjusugangsincheonghelper.multigame.game.runtime.GameRuntimeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameSupplyService {

	private final GameRuntimeStore runtimeStore;
	private final MultigameProperties multigameProperties;

	public void run() {
		long totalRampUpDurationInSeconds = multigameProperties.getSupply().getTotalRampUpDuration().getSeconds();
		long limit = SupplyCalculator.initialLimit(runtimeStore.waitingCount());
		runtimeStore.setAdmissionLimit(limit);
		for (int elapsedSeconds = 1; elapsedSeconds < totalRampUpDurationInSeconds; elapsedSeconds++) {
			if (!sleepStepInterval()) {
				return;
			}
			limit = SupplyCalculator.nextLimit(limit, runtimeStore.participants(), runtimeStore.queueLength(), (int) (totalRampUpDurationInSeconds - elapsedSeconds));
			runtimeStore.setAdmissionLimit(limit);
		}
	}

	private boolean sleepStepInterval() {
		try {
			long stepSleepIntervalInMillis = multigameProperties.getSupply().getStepSleepInterval().toMillis();
			Thread.sleep(stepSleepIntervalInMillis);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}

