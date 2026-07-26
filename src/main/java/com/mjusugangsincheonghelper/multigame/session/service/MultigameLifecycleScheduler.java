package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.multigame.common.GameTimeCalculator;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import com.mjusugangsincheonghelper.multigame.session.domain.GameState;
import com.mjusugangsincheonghelper.multigame.session.domain.HeartbeatLedger;
import com.mjusugangsincheonghelper.multigame.session.domain.MultigameStateEngine;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!dev")  // dev 환경 제외: prod, staging, test 등 모든 비개발 환경에서 활성화
public class MultigameLifecycleScheduler {

	private static final String WAITING_CRON = "0 5/10 * * * *";
	private static final String READY_CRON = "50 9/10 * * * *";
	private static final String PROGRESS_CRON = "0 0/10 * * * *";
	private static final String ENDING_CRON = "20 0/10 * * * *";

	private final StringRedisTemplate stringRedisTemplate;
	private final SupplyEngineService supplyEngineService;
	private final MultigameFinalizeService finalizeService;
	private final MultigameReservationRepository reservationRepository;
	private final TaskScheduler multigameScheduler;
	private final MultigameStateEngine stateEngine;
	private final HeartbeatLedger heartbeatLedger;

	public MultigameLifecycleScheduler(
			StringRedisTemplate stringRedisTemplate,
			SupplyEngineService supplyEngineService,
			MultigameFinalizeService finalizeService,
			MultigameReservationRepository reservationRepository,
			@Qualifier("multigameScheduler") TaskScheduler multigameScheduler,
			MultigameStateEngine stateEngine,
			HeartbeatLedger heartbeatLedger) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.supplyEngineService = supplyEngineService;
		this.finalizeService = finalizeService;
		this.reservationRepository = reservationRepository;
		this.multigameScheduler = multigameScheduler;
		this.stateEngine = stateEngine;
		this.heartbeatLedger = heartbeatLedger;
	}

	@PostConstruct
	public void init() {
		multigameScheduler.schedule(this::waitingJob, new CronTrigger(WAITING_CRON));
		multigameScheduler.schedule(this::readyJob, new CronTrigger(READY_CRON));
		multigameScheduler.schedule(this::progressJob, new CronTrigger(PROGRESS_CRON));
		multigameScheduler.schedule(this::endingJob, new CronTrigger(ENDING_CRON));
		recoverOnStartup();
	}

	private void recoverOnStartup() {
		ScanOptions scanOptions = ScanOptions.scanOptions()
				.match("multigame::*::state::control")
				.count(100)
				.build();

		try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
			while (cursor.hasNext()) {
				String key = cursor.next();
				String stateStr = stringRedisTemplate.opsForValue().get(key);
				GameState state = GameState.fromString(stateStr);
				if (state == null || state.isTerminal()) continue;

				String t = key.replace("multigame::", "").replace("::state::control", "");
				stateEngine.cancelGame(t);
				log.warn("Server restart recovery: game {} state {} -> CANCELLED", t, state);
			}
		} catch (Exception e) {
			log.error("Server restart recovery: error scanning state keys", e);
		}
	}

	void waitingJob() {
		String t = GameTimeCalculator.computeNextT(LocalDateTime.now());
		stateEngine.tryExecuteWithLock("waitingJob", t, () -> {
			GameState state = stateEngine.getState(t);

			if (state == null) {
				List<MultigameReservationEntity> reservations = reservationRepository.findByStartTime(t);
				if (!reservations.isEmpty()) {
					initializeGame(t, reservations.size());
					log.info("WaitingJob: game {} initialized with {} participants", t, reservations.size());
				} else {
					stateEngine.cancelGame(t);
					log.info("WaitingJob: game {} cancelled (no reservations)", t);
				}
				return;
			}

			switch (state) {
				case WAITING, CANCELLED, FINALIZE -> {}
				case READY, PROGRESS, ENDED -> {
					stateEngine.cancelGame(t);
					log.warn("WaitingJob: game {} state {} -> CANCELLED", t, state);
				}
			}
		});
	}

	void readyJob() {
		String t = GameTimeCalculator.computeNextT(LocalDateTime.now());
		stateEngine.tryExecuteWithLock("readyJob", t, () -> {
			GameState state = stateEngine.getState(t);

			if (state == null) {
				stateEngine.cancelGame(t);
				return;
			}

			switch (state) {
				case WAITING -> {
					int participants = heartbeatLedger.countActiveHeartbeats(t);
					if (participants >= 2) {
						heartbeatLedger.saveParticipantSnapshot(t, participants);
						stateEngine.transitionTo(t, GameState.READY);
						log.info("ReadyJob: game {} -> READY ({} participants)", t, participants);
					} else {
						stateEngine.cancelGame(t);
						log.info("ReadyJob: game {} -> CANCELLED ({} participants < 2)", t, participants);
					}
				}
				case READY, CANCELLED, FINALIZE, ENDED -> {}
				case PROGRESS -> {
					stateEngine.cancelGame(t);
					log.warn("ReadyJob: game {} PROGRESS -> CANCELLED", t);
				}
			}
		});
	}

	void progressJob() {
		String t = GameTimeCalculator.computeCurrentT(LocalDateTime.now());
		stateEngine.tryExecuteWithSessionLock("progressJob", t, () -> {
			GameState state = stateEngine.getState(t);

			if (state == null) {
				stateEngine.cancelGame(t);
				return;
			}

			switch (state) {
				case READY -> {
					stateEngine.transitionTo(t, GameState.PROGRESS);
					int participants = heartbeatLedger.getParticipantSnapshot(t);
					supplyEngineService.execute(t, participants);
					log.info("ProgressJob: game {} PROGRESS completed", t);
				}
				case PROGRESS, CANCELLED, FINALIZE -> {}
				case WAITING, ENDED -> {
					stateEngine.cancelGame(t);
					log.warn("ProgressJob: game {} {} -> CANCELLED", t, state);
				}
			}
		});
	}

	void endingJob() {
		String t = GameTimeCalculator.computeCurrentT(LocalDateTime.now());
		stateEngine.tryExecuteWithLock("endingJob", t, () -> {
			GameState state = stateEngine.getState(t);

			if (state == null) {
				stateEngine.cancelGame(t);
				return;
			}

			switch (state) {
				case PROGRESS -> {
					stateEngine.transitionTo(t, GameState.ENDED);
					log.info("EndingJob: game {} -> ENDED", t);
					finalizeService.finalizeGame(t);
				}
				case ENDED, CANCELLED, FINALIZE -> {}
				case WAITING, READY -> {
					stateEngine.cancelGame(t);
					log.warn("EndingJob: game {} {} -> CANCELLED", t, state);
				}
			}
		});
	}

	void initializeGame(String t, int participantCount) {
		MultigameRedisKeyProvider.initializeGameSession(stringRedisTemplate, stateEngine, t, participantCount);
	}
}
