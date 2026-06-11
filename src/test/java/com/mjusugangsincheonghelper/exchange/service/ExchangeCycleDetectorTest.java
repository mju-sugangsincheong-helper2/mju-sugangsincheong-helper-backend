package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExchangeCycleDetector 단위 테스트")
class ExchangeCycleDetectorTest {

	@Mock
	private ExchangeIntentRepository intentRepository;

	@Mock
	private ExchangeRoomRepository roomRepository;

	@Mock
	private ExchangeRoomCreationService roomCreationService;

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
			createdAtField.set(entity, java.time.Instant.now());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return entity;
	}

	@Nested
	@DisplayName("detectCyclesAndCreateRooms 메서드는")
	class Describe_detectCyclesAndCreateRooms {

		@Test
		@DisplayName("2인 순환을 탐지하고 방을 생성한다: A→B, B→A")
		void it_detects_two_person_cycle_and_creates_room() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10001");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(intent1, intent2));
			given(roomRepository.findByTermAndCycleHash(anyString(), anyString()))
					.willReturn(Optional.empty());
			given(roomCreationService.createRoom(anyString(), anyList(), anyString()))
					.willReturn(1L);

			cycleDetector.detectCyclesAndCreateRooms(TERM, intent1);

			verify(roomCreationService).createRoom(anyString(), anyList(), anyString());
		}

		@Test
		@DisplayName("3인 순환을 탐지하고 방을 생성한다: A→B, B→C, C→A")
		void it_detects_three_person_cycle_and_creates_room() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10003");
			ExchangeIntentEntity intent3 = createIntent(3L, 300L, "10003", "10001");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(intent1, intent2, intent3));
			given(roomRepository.findByTermAndCycleHash(anyString(), anyString()))
					.willReturn(Optional.empty());
			given(roomCreationService.createRoom(anyString(), anyList(), anyString()))
					.willReturn(1L);

			cycleDetector.detectCyclesAndCreateRooms(TERM, intent1);

			verify(roomCreationService).createRoom(anyString(), anyList(), anyString());
		}

		@Test
		@DisplayName("순환이 없으면 방을 생성하지 않는다")
		void it_does_not_create_room_when_no_cycle() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10003");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(intent1, intent2));

			cycleDetector.detectCyclesAndCreateRooms(TERM, intent1);

			verify(roomCreationService, never()).createRoom(anyString(), anyList(), anyString());
		}

		@Test
		@DisplayName("이미 존재하는 방은 중복 생성하지 않는다")
		void it_skips_existing_room() {
			ExchangeIntentEntity intent1 = createIntent(1L, 100L, "10001", "10002");
			ExchangeIntentEntity intent2 = createIntent(2L, 200L, "10002", "10001");

			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(intent1, intent2));
			given(roomRepository.findByTermAndCycleHash(anyString(), anyString()))
					.willReturn(Optional.of(org.mockito.Mockito.mock(com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity.class)));

			cycleDetector.detectCyclesAndCreateRooms(TERM, intent1);

			verify(roomCreationService, never()).createRoom(anyString(), anyList(), anyString());
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
