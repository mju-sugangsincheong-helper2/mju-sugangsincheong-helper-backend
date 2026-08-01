package com.mjusugangsincheonghelper.multigame.game.service;

import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.game.config.MultigameProperties;
import com.mjusugangsincheonghelper.multigame.game.domain.RoundTime;
import com.mjusugangsincheonghelper.multigame.game.domain.RuntimeState;
import com.mjusugangsincheonghelper.multigame.game.runtime.GameRuntimeStore;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundSettlement;
import com.mjusugangsincheonghelper.multigame.result.service.RoundSettlementService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class GameLifecycleScheduler {

	private final AdvisoryLockService advisoryLockService;
	private final TransactionTemplate transactionTemplate;
	private final GameRuntimeStore runtimeStore;
	private final GameSupplyService supplyService;
	private final RoundSettlementService settlementService;
	private final MultigameProperties properties;

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
		// GameStatusResolver와 동일 소스(app.multigame.start-close/end-close)로 미운영 시간대를 판단한다.
		LocalTime time = now.toLocalTime();
		LocalTime closedStart = properties.getStartClose();
		LocalTime closedEnd = properties.getEndClose();
		if (closedStart.equals(closedEnd)) {
			return false; // start-close == end-close 이면 미운영 시간대 없음 (24시간 운영)
		}
		if (closedStart.isBefore(closedEnd)) {
			return !time.isBefore(closedStart) && time.isBefore(closedEnd);
		}
		// 자정을 넘기는 설정 (예: 23:00 ~ 01:00) — 시작 이후 자정까지, 또는 자정부터 종료 전까지 CLOSED
		return !time.isBefore(closedStart) || time.isBefore(closedEnd);
	}
}
