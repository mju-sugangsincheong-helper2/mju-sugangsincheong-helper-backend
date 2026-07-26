package com.mjusugangsincheonghelper.multigame.session.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultRepository;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import com.mjusugangsincheonghelper.multigame.session.domain.GameState;
import com.mjusugangsincheonghelper.multigame.session.domain.HeartbeatLedger;
import com.mjusugangsincheonghelper.multigame.session.domain.MultigameStateEngine;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.lenient;
import java.util.function.Consumer;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameFinalizeService 테스트")
class MultigameFinalizeServiceTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private HashOperations<String, Object, Object> hashOperations;

	@Mock
	private MultigameResultRepository resultRepository;

	@Mock
	private MultigameResultDetailRepository resultDetailRepository;

	@Mock
	private MultigameStateEngine stateEngine;

	@Mock
	private HeartbeatLedger heartbeatLedger;

	@Mock
	private TransactionTemplate transactionTemplate;

	@InjectMocks
	private MultigameFinalizeService finalizeService;

	@BeforeEach
	void setUp() {
		lenient().doAnswer(invocation -> {
			Consumer<TransactionStatus> action = invocation.getArgument(0);
			action.accept(null);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());
	}

	@Nested
	@DisplayName("finalizeGame 메서드는")
	class Describe_finalizeGame {

		@Test
		@DisplayName("ENDED 상태이면 결과를 저장하고 FINALIZE로 전이한다")
		void it_saves_results_and_transitions_to_finalize_when_state_is_ended() {
			// Given
			String t = "20260726100000";

			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);
			given(heartbeatLedger.getParticipantSnapshot(t)).willReturn(10);
			given(stateEngine.getState(t)).willReturn(GameState.ENDED);
			given(hashOperations.entries(MultigameRedisKeyProvider.history(t))).willReturn(Map.of());

			given(resultRepository.findById(t)).willReturn(Optional.empty());

			// When
			finalizeService.finalizeGame(t);

			// Then
			verify(stateEngine).transitionTo(t, GameState.FINALIZE);
			verify(resultRepository).save(any(MultigameResultEntity.class));
		}

		@Test
		@DisplayName("FINALIZE 상태이면 아무 작업도 하지 않는다")
		void it_does_nothing_when_state_is_finalize() {
			// Given
			String t = "20260726100000";

			given(stateEngine.getState(t)).willReturn(GameState.FINALIZE);

			// When
			finalizeService.finalizeGame(t);

			// Then
			verify(resultRepository, never()).save(any());
		}

		@Test
		@DisplayName("CANCELLED 상태이면 아무 작업도 하지 않는다")
		void it_does_nothing_when_state_is_cancelled() {
			// Given
			String t = "20260726100000";

			given(stateEngine.getState(t)).willReturn(GameState.CANCELLED);

			// When
			finalizeService.finalizeGame(t);

			// Then
			verify(resultRepository, never()).save(any());
		}

		@Test
		@DisplayName("상태가 null이면 CANCELLED로 설정한다")
		void it_sets_cancelled_when_state_is_null() {
			// Given
			String t = "20260726100000";

			given(stateEngine.getState(t)).willReturn(null);

			// When
			finalizeService.finalizeGame(t);

			// Then
			verify(stateEngine).cancelGame(t);
		}

		@Test
		@DisplayName("비정상 상태(WAITING)이면 CANCELLED로 설정한다")
		void it_sets_cancelled_for_abnormal_state() {
			// Given
			String t = "20260726100000";

			given(stateEngine.getState(t)).willReturn(GameState.WAITING);

			// When
			finalizeService.finalizeGame(t);

			// Then
			verify(stateEngine).cancelGame(t);
		}

		@Test
		@DisplayName("history에 SUCCESS 결과가 있으면 DB에 저장한다")
		void it_saves_success_result_to_db() {
			// Given
			String t = "20260726100000";
			Map<Object, Object> history = Map.of(
					"1", "SUCCESS:3:1234567890"
			);

			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);
			given(heartbeatLedger.getParticipantSnapshot(t)).willReturn(10);
			given(stateEngine.getState(t)).willReturn(GameState.ENDED);
			given(hashOperations.entries(MultigameRedisKeyProvider.history(t))).willReturn(history);

			given(resultRepository.findById(t)).willReturn(Optional.empty());
			given(resultDetailRepository.findByStartTimeAndMemberId(t, 1L)).willReturn(Optional.empty());

			// When
			finalizeService.finalizeGame(t);

			// Then
			verify(resultDetailRepository).save(any(MultigameResultDetailEntity.class));
		}

		@Test
		@DisplayName("이미 결과가 존재하면 저장하지 않는다 (멱등성)")
		void it_does_not_save_if_result_already_exists() {
			// Given
			String t = "20260726100000";
			Map<Object, Object> history = Map.of(
					"1", "SUCCESS:3:1234567890"
			);

			MultigameResultDetailEntity existingEntity = MultigameResultDetailEntity.builder()
					.startTime(t)
					.memberId(1L)
					.subjectId(3)
					.status("SUCCESS")
					.build();

			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);
			given(heartbeatLedger.getParticipantSnapshot(t)).willReturn(10);
			given(stateEngine.getState(t)).willReturn(GameState.ENDED);
			given(hashOperations.entries(MultigameRedisKeyProvider.history(t))).willReturn(history);

			given(resultRepository.findById(t)).willReturn(Optional.empty());
			given(resultDetailRepository.findByStartTimeAndMemberId(t, 1L)).willReturn(Optional.of(existingEntity));

			// When
			finalizeService.finalizeGame(t);

			// Then
			verify(resultDetailRepository, never()).save(any());
		}
	}
}
