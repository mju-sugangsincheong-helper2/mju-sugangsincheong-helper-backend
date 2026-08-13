package com.mjusugangsincheonghelper.multigame.game.service;

import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.game.domain.GameStatusResolver;
import com.mjusugangsincheonghelper.multigame.game.domain.RoundTime;
import com.mjusugangsincheonghelper.multigame.game.domain.RuntimeState;
import com.mjusugangsincheonghelper.multigame.game.runtime.GameRuntimeStore;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundSettlement;
import com.mjusugangsincheonghelper.multigame.result.service.RoundSettlementService;
import java.time.Instant;
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
	private final GameStatusResolver statusResolver;

	@Scheduled(cron = "${app.multigame.schedule.game-ready-cron:${app.schedule.game-ready.cron:55 9/10 * * * *}}", zone = "Asia/Seoul", scheduler = "multigameScheduler")
	void ready() {
		Instant now = Instant.now();
		if (statusResolver.isClosed(now)) {
			return;
		}
		String round = RoundTime.target(now);
		runInTransaction("multigame-ready", round, () -> {
			long waitingCount = runtimeStore.removeExpiredHeartbeatsAndCount(Instant.now());
			if (waitingCount < 2) {
				runtimeStore.setState(RuntimeState.CANCELLED);
				log.debug("Multigame round cancelled (not enough players). round={}, waitingCount={}", round, waitingCount);
				return;
			}
			runtimeStore.saveWaitingCount(waitingCount);
			runtimeStore.setState(RuntimeState.READY);
			log.info("Multigame round ready. round={}, waitingCount={}", round, waitingCount);
		});
	}

	@Scheduled(cron = "${app.multigame.schedule.game-start-cron:${app.schedule.game-start.cron:0 0/10 * * * *}}", zone = "Asia/Seoul", scheduler = "multigameScheduler")
	void start() {
		Instant now = Instant.now();
		if (statusResolver.isClosed(now)) {
			return;
		}
		String round = RoundTime.currentMark(now);
		AdvisoryLockService.SessionLock lock = advisoryLockService.trySessionLockHeld("multigame-progress", round);
		if (lock == null) {
			return;
		}
		try (lock) {
			if (runtimeStore.state() != RuntimeState.READY) {
				log.debug("Multigame round cancelled (not ready). round={}, state={}", round, runtimeStore.state());
				runtimeStore.setState(RuntimeState.CANCELLED);
				return;
			}
			long waitingCount = runtimeStore.waitingCount();
			runtimeStore.startProgress(capacity(waitingCount));
			log.info("Multigame round started. round={}, waitingCount={}, capacity={}", round, waitingCount, capacity(waitingCount));
			supplyService.run();
		}
	}

	@Scheduled(cron = "${app.multigame.schedule.game-finish-cron:${app.schedule.game-finish.cron:30 0/10 * * * *}}", zone = "Asia/Seoul", scheduler = "multigameScheduler")
	void finish() {
		Instant now = Instant.now();
		if (statusResolver.isClosed(now)) {
			return;
		}
		String round = RoundTime.currentMark(now);
		runInTransaction("multigame-finish", round, () -> {
			if (runtimeStore.state() != RuntimeState.PROGRESS) {
				return;
			}
			long waitingCount = runtimeStore.waitingCount();
			long participantCount = runtimeStore.participants();
			settlementService.save(RoundSettlement.from(round, (int) participantCount, capacity(waitingCount), runtimeStore.eventLog()));
			runtimeStore.clear();
			log.info("Multigame round settled. round={}, participantCount={}, capacity={}", round, participantCount, capacity(waitingCount));
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
}
