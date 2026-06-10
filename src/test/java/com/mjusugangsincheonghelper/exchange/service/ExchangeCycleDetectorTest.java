package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeCycleDetector 단위 테스트")
class ExchangeCycleDetectorTest {

	@Mock
	private ExchangeIntentRepository intentRepository;

	@InjectMocks
	private ExchangeCycleDetector cycleDetector;

	private static final String TERM = "202510";

	private ExchangeIntentEntity createIntent(Long id, Long memberId, String give, String want) {
		ExchangeIntentEntity entity = ExchangeIntentEntity.builder()
				.term(TERM)
				.memberId(memberId)
				.giveCourseNo(give)
				.wantCourseNo(want)
				.build();

		try {
			var idField = ExchangeIntentEntity.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(entity, id);

			var createdAtField = ExchangeIntentEntity.class.getDeclaredField("createdAt");
			createdAtField.setAccessible(true);
			createdAtField.set(entity, Instant.now());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return entity;
	}

	@Nested
	@DisplayName("detectCycles 메서드는")
	class Describe_detectCycles {

		@Test
		@DisplayName("2인 순환을 탐지한다: A→B, B→A")
		void it_detects_two_person_cycle() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10001");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(intent1, intent2));

			List<List<ExchangeIntentEntity>> cycles = cycleDetector.detectCycles(TERM, intent1);

			assertThat(cycles).hasSize(1);
			assertThat(cycles.get(0)).containsExactly(intent2);
		}

		@Test
		@DisplayName("3인 순환을 탐지한다: A→B, B→C, C→A")
		void it_detects_three_person_cycle() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10003");
			ExchangeIntentEntity intent3 = createIntent(3L, 300L, "10003", "10001");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(intent1, intent2, intent3));

			List<List<ExchangeIntentEntity>> cycles = cycleDetector.detectCycles(TERM, intent1);

			assertThat(cycles).hasSize(1);
			assertThat(cycles.get(0)).containsExactly(intent2, intent3);
		}

		@Test
		@DisplayName("순환이 없으면 빈 리스트를 반환한다")
		void it_returns_empty_when_no_cycle() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10003");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(intent1, intent2));

			List<List<ExchangeIntentEntity>> cycles = cycleDetector.detectCycles(TERM, intent1);

			assertThat(cycles).isEmpty();
		}

		@Test
		@DisplayName("새 의도가 순환의 시작점이 되어야 한다")
		void it_detects_cycle_starting_from_new_intent() {
			ExchangeIntentEntity existingIntent = createIntent(1L, 200L, "10002", "10001");
			ExchangeIntentEntity newIntent = createIntent(2L, 100L, "10001", "10002");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(existingIntent, newIntent));

			List<List<ExchangeIntentEntity>> cycles = cycleDetector.detectCycles(TERM, newIntent);

			assertThat(cycles).hasSize(1);
			assertThat(cycles.get(0)).containsExactly(existingIntent);
		}

		@Test
		@DisplayName("복잡한 그래프에서 하나의 순환만 탐지한다")
		void it_detects_single_cycle_in_complex_graph() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10003");
			ExchangeIntentEntity intent3 = createIntent(3L, 300L, "10003", "10001");
			ExchangeIntentEntity intent4 = createIntent(4L, 400L, "10004", "10005");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(intent1, intent2, intent3, intent4));

			List<List<ExchangeIntentEntity>> cycles = cycleDetector.detectCycles(TERM, intent1);

			assertThat(cycles).hasSize(1);
		}

		@Test
		@DisplayName("동일한 과목 번호를 가진 여러 의도가 있어도 정확히 탐지한다")
		void it_handles_multiple_intents_with_same_course() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10001");
			ExchangeIntentEntity intent3 = createIntent(3L, 300L, "10002", "10003");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(intent1, intent2, intent3));

			List<List<ExchangeIntentEntity>> cycles = cycleDetector.detectCycles(TERM, intent1);

			assertThat(cycles).hasSize(1);
			assertThat(cycles.get(0)).containsExactly(intent2);
		}
	}

	@Nested
	@DisplayName("computeCycleHash 메서드는")
	class Describe_computeCycleHash {

		@Test
		@DisplayName("동일한 사이클에 대해 동일한 해시를 반환한다")
		void it_returns_same_hash_for_same_cycle() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10001");

			String hash1 = cycleDetector.computeCycleHash(List.of(intent1, intent2));
			String hash2 = cycleDetector.computeCycleHash(List.of(intent2, intent1));

			assertThat(hash1).isEqualTo(hash2);
		}

		@Test
		@DisplayName("다른 사이클에 대해 다른 해시를 반환한다")
		void it_returns_different_hash_for_different_cycle() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10001");
			ExchangeIntentEntity intent3 = createIntent(3L, 300L, "10003", "10004");

			String hash1 = cycleDetector.computeCycleHash(List.of(intent1, intent2));
			String hash2 = cycleDetector.computeCycleHash(List.of(intent1, intent3));

			assertThat(hash1).isNotEqualTo(hash2);
		}

		@Test
		@DisplayName("64자리 SHA-256 해시를 반환한다")
		void it_returns_64_char_sha256_hash() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10001");

			String hash = cycleDetector.computeCycleHash(List.of(intent1, intent2));

			assertThat(hash).hasSize(64);
			assertThat(hash).matches("[0-9a-f]+");
		}
	}
}
