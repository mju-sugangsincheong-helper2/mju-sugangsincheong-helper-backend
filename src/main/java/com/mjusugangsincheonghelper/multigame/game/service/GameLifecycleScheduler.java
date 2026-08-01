package com.mjusugangsincheonghelper.multigame.game.service;

import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.game.domain.RoundTime;
import com.mjusugangsincheonghelper.multigame.game.domain.RuntimeState;
import com.mjusugangsincheonghelper.multigame.game.runtime.GameRuntimeStore;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundSettlement;
import com.mjusugangsincheonghelper.multigame.result.service.RoundSettlementService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameLifecycleScheduler {

	private final AdvisoryLockService advisoryLockService;
	private final TransactionTemplate transactionTemplate;
	private final GameRuntimeStore runtimeStore;
	private final GameSupplyService supplyService;
	private final RoundSettlementService settlementService;

	@Scheduled(cron = "55 9/10 * * * *", scheduler = "multigameScheduler")
	void ready() {
		LocalDateTime now = LocalDateTime.now();
		if (closed(now)) {
			return;
		}
		String round = RoundTime.target(now);
		runInTransaction("multigame-ready", round, () -> {
			long waitingCount = runtimeStore.removeExpiredHeartbeatsAndCount(Instant.now());
			if (waitingCount < 2) {
				runtimeStore.setState(RuntimeState.CANCELLED);
				return;
			}
			runtimeStore.saveWaitingCount(waitingCount);
			runtimeStore.setState(RuntimeState.READY);
		});
	}

	@Scheduled(cron = "0 0/10 * * * *", scheduler = "multigameScheduler")
	void start() {
		LocalDateTime now = LocalDateTime.now();
		if (closed(now)) {
			return;
		}
		String round = RoundTime.currentMark(now);
		AdvisoryLockService.SessionLock lock = advisoryLockService.trySessionLockHeld("multigame-progress", round);
		if (lock == null) {
			return;
		}
		try (lock) {
			if (runtimeStore.state() != RuntimeState.READY) {
				runtimeStore.setState(RuntimeState.CANCELLED);
				return;
			}
			long waitingCount = runtimeStore.waitingCount();
			runtimeStore.startProgress(capacity(waitingCount));
			supplyService.run();
		}
	}

	@Scheduled(cron = "30 0/10 * * * *", scheduler = "multigameScheduler")
	void finish() {
		LocalDateTime now = LocalDateTime.now();
		if (closed(now)) {
			return;
		}
		String round = RoundTime.currentMark(now);
		runInTransaction("multigame-finish", round, () -> {
			if (runtimeStore.state() != RuntimeState.PROGRESS) {
				return;
			}
			long waitingCount = runtimeStore.waitingCount();
			settlementService.save(RoundSettlement.from(round, (int) runtimeStore.participants(), capacity(waitingCount), runtimeStore.eventLog()));
			runtimeStore.clear();
		});
	}

	private int capacity(long waitingCount) {
		return Math.max(1, (int) Math.round(waitingCount / 2.0));
	}

	private void runInTransaction(String action, String round, Runnable task) {
		transactionTemplate.executeWithoutResult(status -> {
			if (advisoryLockService.tryXactLock(action, round)) {
				task.run();
			}
		});
	}

	private boolean closed(LocalDateTime now) {
		LocalTime time = now.toLocalTime();
		return !time.isBefore(LocalTime.of(2, 0)) && time.isBefore(LocalTime.of(5, 0));
	}
}
