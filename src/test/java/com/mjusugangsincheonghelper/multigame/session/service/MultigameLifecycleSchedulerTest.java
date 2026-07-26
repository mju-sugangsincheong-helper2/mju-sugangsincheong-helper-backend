package com.mjusugangsincheonghelper.multigame.session.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import com.mjusugangsincheonghelper.multigame.session.domain.GameState;
import com.mjusugangsincheonghelper.multigame.session.domain.HeartbeatLedger;
import com.mjusugangsincheonghelper.multigame.session.domain.MultigameStateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameLifecycleScheduler 테스트")
class MultigameLifecycleSchedulerTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private AdvisoryLockService advisoryLockService;

	@Mock
	private SupplyEngineService supplyEngineService;

	@Mock
	private MultigameFinalizeService finalizeService;

	@Mock
	private MultigameReservationRepository reservationRepository;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private HashOperations<String, Object, Object> hashOperations;

	@Mock
	private MultigameStateEngine stateEngine;

	@Mock
	private HeartbeatLedger heartbeatLedger;

	@Nested
	@DisplayName("initializeGame 메서드는")
	class Describe_initializeGame {

		@Test
		@DisplayName("참여자 10명일 때 capacity=5, initialLimit=2로 설정한다")
		void it_initializes_with_correct_capacity_and_limit() throws Exception {
			// Given
			String t = "20260726100000";
			int participantCount = 10;

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);

			MultigameLifecycleScheduler scheduler = createScheduler();

			// When
			scheduler.initializeGame(t, participantCount);

			// Then
			verify(stateEngine).setState(t, GameState.WAITING);
			verify(valueOperations).set(MultigameRedisKeyProvider.seq(t), "0");
			verify(valueOperations).set(MultigameRedisKeyProvider.admissionLimit(t), "2");
			verify(hashOperations).putAll(eq(MultigameRedisKeyProvider.seats(t)), any());
		}
	}

	private MultigameLifecycleScheduler createScheduler() {
		return new MultigameLifecycleScheduler(
				stringRedisTemplate,
				supplyEngineService,
				finalizeService,
				reservationRepository,
				null,
				stateEngine,
				heartbeatLedger
		);
	}
}
