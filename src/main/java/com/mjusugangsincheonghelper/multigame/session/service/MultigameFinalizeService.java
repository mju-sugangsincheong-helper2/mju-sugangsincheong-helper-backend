package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultRepository;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import com.mjusugangsincheonghelper.multigame.session.domain.GameState;
import com.mjusugangsincheonghelper.multigame.session.domain.HeartbeatLedger;
import com.mjusugangsincheonghelper.multigame.session.domain.MultigameStateEngine;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultigameFinalizeService {

	private final StringRedisTemplate stringRedisTemplate;
	private final MultigameResultRepository resultRepository;
	private final MultigameResultDetailRepository resultDetailRepository;
	private final MultigameStateEngine stateEngine;
	private final HeartbeatLedger heartbeatLedger;
	private final TransactionTemplate transactionTemplate;

	public void finalizeGame(String t) {
		GameState state = stateEngine.getState(t);

		if (state == null) {
			stateEngine.cancelGame(t);
			return;
		}

		switch (state) {
			case ENDED -> {
				upsertResultsInNewTransaction(t);
				stateEngine.transitionTo(t, GameState.FINALIZE);
				log.info("FinalizeJob: game {} -> FINALIZE", t);
			}
			case FINALIZE, CANCELLED -> {
			}
			default -> {
				stateEngine.cancelGame(t);
			}
		}
	}

	public void upsertResultsInNewTransaction(String t) {
		transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		transactionTemplate.executeWithoutResult(status -> upsertResults(t));
	}

	private void upsertResults(String t) {
		Map<Object, Object> history = stringRedisTemplate.opsForHash().entries(MultigameRedisKeyProvider.history(t));

		for (Map.Entry<Object, Object> entry : history.entrySet()) {
			Long memberId = Long.parseLong(entry.getKey().toString());
			String value = entry.getValue().toString();
			String[] parts = value.split(":");
			String status = parts[0];
			int subjectId = Integer.parseInt(parts[1]);

			resultDetailRepository.findByStartTimeAndMemberId(t, memberId)
					.ifPresentOrElse(
							existing -> {
							},
							() -> resultDetailRepository.save(
									MultigameResultDetailEntity.builder()
											.startTime(t)
											.memberId(memberId)
											.subjectId(subjectId)
											.status(status)
											.build()
							)
					);
		}

		int participantCount = heartbeatLedger.getParticipantSnapshot(t);
		int capacity = Math.max(1, participantCount / 2);

		resultRepository.findById(t)
				.ifPresentOrElse(
						entity -> entity.finalizeResult(Instant.now()),
						() -> resultRepository.save(
								MultigameResultEntity.builder()
										.startTime(t)
										.participantCount(participantCount)
										.capacity(capacity)
										.finalizedAt(Instant.now())
										.build()
						)
				);
	}
}
