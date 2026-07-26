package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultRepository;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultigameFinalizeService {

	private final StringRedisTemplate stringRedisTemplate;
	private final MultigameResultRepository resultRepository;
	private final MultigameResultDetailRepository resultDetailRepository;

	@Lazy
	@Autowired
	private MultigameFinalizeService self;

	public void finalizeGame(String t) {
		String stateKey = MultigameRedisKeyProvider.state(t);
		String state = stringRedisTemplate.opsForValue().get(stateKey);

		if (state == null) {
			stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
			return;
		}

		switch (state) {
			case "ENDED" -> {
				self.upsertResultsInNewTransaction(t);
				stringRedisTemplate.opsForValue().set(stateKey, "FINALIZE");
				log.info("FinalizeJob: game {} -> FINALIZE", t);
			}
			case "FINALIZE", "CANCELLED" -> {
			}
			default -> {
				stringRedisTemplate.opsForValue().set(stateKey, "CANCELLED");
			}
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void upsertResultsInNewTransaction(String t) {
		upsertResults(t);
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

		int participantCount = countHeartbeats(t);
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

	private int countHeartbeats(String t) {
		String pattern = MultigameRedisKeyProvider.heartbeatPattern(t);
		ScanOptions scanOptions = ScanOptions.scanOptions()
				.match(pattern)
				.count(100)
				.build();

		try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
			int count = 0;
			while (cursor.hasNext()) {
				cursor.next();
				count++;
			}
			return count;
		} catch (Exception e) {
			log.error("Failed to count heartbeats for t={}", t, e);
			return 0;
		}
	}
}
