package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.common.GameTimeCalculator;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!dev")
public class MultigameLifecycleScheduler {

	private static final int SUBJECT_COUNT = 6;
	private static final String WAITING_CRON = "0 5/10 * * * *";
	private static final String READY_CRON = "50 9/10 * * * *";
	private static final String PROGRESS_CRON = "0 0/10 * * * *";
	private static final String ENDING_CRON = "20 0/10 * * * *";

	private final StringRedisTemplate stringRedisTemplate;
	private final AdvisoryLockService advisoryLockService;
	private final SupplyEngineService supplyEngineService;
	private final MultigameFinalizeService finalizeService;
	private final MultigameReservationRepository reservationRepository;

	@Qualifier("multigameScheduler")
	private final TaskScheduler multigameScheduler;

	@PostConstruct
	public void init() {
		multigameScheduler.schedule(this::waitingJob, new CronTrigger(WAITING_CRON));
		multigameScheduler.schedule(this::readyJob, new CronTrigger(READY_CRON));
		multigameScheduler.schedule(this::progressJob, new CronTrigger(PROGRESS_CRON));
		multigameScheduler.schedule(this::endingJob, new CronTrigger(ENDING_CRON));
		recoverOnStartup();
	}

	private void recoverOnStartup() {
		Set<String> stateKeys = stringRedisTemplate.keys("multigame::*::state::control");
		if (stateKeys == null || stateKeys.isEmpty()) return;

		LocalDateTime now = LocalDateTime.now();
		String currentT = GameTimeCalculator.computeCurrentT(now);

		for (String key : stateKeys) {
			String t = key.replace("multigame::", "").replace("::state::control", "");
			String state = stringRedisTemplate.opsForValue().get(key);

			if (state == null) continue;

			if (t.compareTo(currentT) < 0
					|| "WAITING".equals(state)
					|| "READY".equals(state)
					|| "PROGRESS".equals(state)
					|| "ENDED".equals(state)) {
				stringRedisTemplate.opsForValue().set(key, "CANCELLED");
				log.warn("Server restart recovery: game {} state {} -> CANCELLED", t, state);
			}
		}
	}

	private void waitingJob() {
		String t = GameTimeCalculator.computeNextT(LocalDateTime.now());
		String stateKey = MultigameRedisKeyProvider.state(t);

		if (!advisoryLockService.tryXactLock("waitingJob", t)) return;

		try {
			String state = stringRedisTemplate.opsForValue().get(stateKey);

			if (state == null) {
				List<MultigameReservationEntity> reservations = reservationRepository.findByStartTime(t);
				if (!reservations.isEmpty()) {
					initializeGame(t, reservations.size());
					log.info("WaitingJob: game {} initialized with {} participants", t, reservations.size());
				} else {
					stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
					log.info("WaitingJob: game {} cancelled (no reservations)", t);
				}
				return;
			}

			switch (state) {
				case "WAITING", "CANCELLED", "FINALIZE" -> {
				}
				case "READY", "PROGRESS", "ENDED" -> {
					stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
					log.warn("WaitingJob: game {} state {} -> CANCELLED", t, state);
				}
				default -> {
					stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
					log.warn("WaitingJob: game {} unknown state {} -> CANCELLED", t, state);
				}
			}
		} finally {
			log.debug("WaitingJob: completed for {}", t);
		}
	}

	private void readyJob() {
		String t = GameTimeCalculator.computeNextT(LocalDateTime.now());
		String stateKey = MultigameRedisKeyProvider.state(t);

		if (!advisoryLockService.tryXactLock("readyJob", t)) return;

		try {
			String state = stringRedisTemplate.opsForValue().get(stateKey);

			if (state == null) {
				stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
				return;
			}

			switch (state) {
				case "WAITING" -> {
					int participants = countHeartbeats(t);
					if (participants >= 2) {
						stringRedisTemplate.opsForValue().set(stateKey, "READY");
						log.info("ReadyJob: game {} -> READY ({} participants)", t, participants);
					} else {
						stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
						log.info("ReadyJob: game {} -> CANCELLED ({} participants < 2)", t, participants);
					}
				}
				case "READY", "CANCELLED", "FINALIZE", "ENDED" -> {
				}
				case "PROGRESS" -> {
					stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
					log.warn("ReadyJob: game {} PROGRESS -> CANCELLED", t);
				}
				default -> {
					stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
				}
			}
		} finally {
			log.debug("ReadyJob: completed for {}", t);
		}
	}

	private void progressJob() {
		String t = GameTimeCalculator.computeCurrentT(LocalDateTime.now());
		String stateKey = MultigameRedisKeyProvider.state(t);

		if (!advisoryLockService.tryXactLock("progressJob", t)) return;

		try {
			String state = stringRedisTemplate.opsForValue().get(stateKey);

			if (state == null) {
				stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
				return;
			}

			switch (state) {
				case "READY" -> {
					if (!advisoryLockService.trySessionLock("progressJob", t)) {
						stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
						return;
					}
					try {
						stringRedisTemplate.opsForValue().set(stateKey, "PROGRESS");
						int participants = countHeartbeats(t);
						supplyEngineService.execute(t, participants);
						log.info("ProgressJob: game {} PROGRESS completed", t);
					} finally {
						advisoryLockService.releaseSessionLock("progressJob", t);
					}
				}
				case "PROGRESS", "CANCELLED", "FINALIZE" -> {
				}
				case "WAITING", "ENDED" -> {
					stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
					log.warn("ProgressJob: game {} {} -> CANCELLED", t, state);
				}
				default -> {
					stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
				}
			}
		} finally {
			log.debug("ProgressJob: completed for {}", t);
		}
	}

	private void endingJob() {
		String t = GameTimeCalculator.computeCurrentT(LocalDateTime.now());
		String stateKey = MultigameRedisKeyProvider.state(t);

		if (!advisoryLockService.tryXactLock("endingJob", t)) return;

		try {
			String state = stringRedisTemplate.opsForValue().get(stateKey);

			if (state == null) {
				stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
				return;
			}

			switch (state) {
				case "PROGRESS" -> {
					stringRedisTemplate.opsForValue().set(stateKey, "ENDED");
					log.info("EndingJob: game {} -> ENDED", t);
					finalizeService.finalizeGame(t);
				}
				case "ENDED", "CANCELLED", "FINALIZE" -> {
				}
				case "WAITING", "READY" -> {
					stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
					log.warn("EndingJob: game {} {} -> CANCELLED", t, state);
				}
				default -> {
					stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
				}
			}
		} finally {
			log.debug("EndingJob: completed for {}", t);
		}
	}

	private void initializeGame(String t, int participantCount) {
		int capacity = Math.max(1, participantCount / 2);
		int initialLimit = Math.max(1, (int) Math.floor(participantCount * 0.2));

		Map<String, String> seats = new HashMap<>();
		for (int i = 1; i <= SUBJECT_COUNT; i++) {
			seats.put(String.valueOf(i), String.valueOf(capacity));
		}

		stringRedisTemplate.opsForValue().set(MultigameRedisKeyProvider.state(t), "WAITING");
		stringRedisTemplate.opsForValue().set(MultigameRedisKeyProvider.seq(t), "0");
		stringRedisTemplate.opsForValue().set(MultigameRedisKeyProvider.admissionLimit(t), String.valueOf(initialLimit));
		stringRedisTemplate.opsForHash().putAll(MultigameRedisKeyProvider.seats(t), seats);
	}

	private int countHeartbeats(String t) {
		Set<String> keys = stringRedisTemplate.keys(MultigameRedisKeyProvider.heartbeatPattern(t));
		return keys != null ? keys.size() : 0;
	}
}
