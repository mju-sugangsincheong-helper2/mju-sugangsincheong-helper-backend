package com.mjusugangsincheonghelper.exchange.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.exchange.dto.CycleDetectionMessage;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeCycleDetector 단위 테스트")
class ExchangeCycleDetectorTest {

	@Mock
	private PgmqService pgmqService;

	@Mock
	private ExchangeIntentRepository intentRepository;

	@Mock
	private ExchangeRoomRepository roomRepository;

	@Mock
	private ExchangeRoomCreationService roomCreationService;

	@InjectMocks
	private ExchangeCycleDetector cycleDetector;

	@Nested
	@DisplayName("enqueueCycleDetection 메서드는")
	class Describe_enqueueCycleDetection {

		@Test
		@DisplayName("메시지를 PGMQ 큐로 전송한다")
		void it_sends_message_to_pgmq() {
			// Given
			CycleDetectionMessage message = CycleDetectionMessage.builder().build();

			// When
			cycleDetector.enqueueCycleDetection(message);

			// Then
			verify(pgmqService).send(ExchangeCycleDetector.QUEUE_NAME, message);
		}
	}

	@Nested
	@DisplayName("detectCyclesAndCreateRooms 메서드는")
	class Describe_detectCyclesAndCreateRooms {

		@Test
		@DisplayName("사이클이 감지되면 방 생성을 요청한다")
		void it_creates_room_when_cycle_is_detected() {
			// Given
			String term = "202510";
			Long triggerIntentId = 1L;

			// 사이클: 1 -> 2 -> 3 -> 1
			ExchangeIntentEntity intent1 = ExchangeIntentEntity.builder()
					.term(term).memberId(10L).giveCourseNo("10001").wantCourseNo("10002")
					.build();
			ReflectionTestUtils.setField(intent1, "id", triggerIntentId);

			ExchangeIntentEntity intent2 = ExchangeIntentEntity.builder()
					.term(term).memberId(20L).giveCourseNo("10002").wantCourseNo("10003")
					.build();
			ReflectionTestUtils.setField(intent2, "id", 2L);

			ExchangeIntentEntity intent3 = ExchangeIntentEntity.builder()
					.term(term).memberId(30L).giveCourseNo("10003").wantCourseNo("10001")
					.build();
			ReflectionTestUtils.setField(intent3, "id", 3L);

			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, triggerIntentId)))
					.willReturn(Optional.of(intent1));

			given(intentRepository.findByTermAndIsDeletedFalse(term))
					.willReturn(List.of(intent1, intent2, intent3));

			String cycleHash = cycleDetector.computeCycleHash(List.of(intent1, intent2, intent3));
			given(roomRepository.findByTermAndCycleHash(term, cycleHash)).willReturn(Optional.empty());

			// When
			cycleDetector.detectCyclesAndCreateRooms(term, triggerIntentId, 10L, "10001", "10002");

			// Then
			verify(roomCreationService).createRoom(eq(term), any(), eq(cycleHash));
		}

		@Test
		@DisplayName("트리거된 Intent가 이미 존재하지 않으면 매칭 처리를 건너뛴다")
		void it_skips_when_trigger_intent_not_found() {
			// Given
			String term = "202510";
			Long triggerIntentId = 1L;

			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, triggerIntentId)))
					.willReturn(Optional.empty());

			// When
			cycleDetector.detectCyclesAndCreateRooms(term, triggerIntentId, 10L, "10001", "10002");

			// Then
			verify(intentRepository, never()).findByTermAndIsDeletedFalse(any());
			verify(roomCreationService, never()).createRoom(any(), any(), any());
		}

		@Test
		@DisplayName("트리거된 Intent가 이미 삭제 상태이면 매칭 처리를 건너뛴다")
		void it_skips_when_trigger_intent_is_deleted() {
			// Given
			String term = "202510";
			Long triggerIntentId = 1L;

			ExchangeIntentEntity intent = ExchangeIntentEntity.builder()
					.term(term).memberId(10L).giveCourseNo("10001").wantCourseNo("10002")
					.build();
			ReflectionTestUtils.setField(intent, "id", triggerIntentId);
			intent.markDeleted();

			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, triggerIntentId)))
					.willReturn(Optional.of(intent));

			// When
			cycleDetector.detectCyclesAndCreateRooms(term, triggerIntentId, 10L, "10001", "10002");

			// Then
			verify(intentRepository, never()).findByTermAndIsDeletedFalse(any());
			verify(roomCreationService, never()).createRoom(any(), any(), any());
		}

		@Test
		@DisplayName("사이클이 존재하지 않으면 방 생성을 건너뛴다")
		void it_skips_when_no_cycle_detected() {
			// Given
			String term = "202510";
			Long triggerIntentId = 1L;

			// 경로만 존재하고 닫힌 루프가 없음: 1 -> 2 -> 3
			ExchangeIntentEntity intent1 = ExchangeIntentEntity.builder()
					.term(term).memberId(10L).giveCourseNo("10001").wantCourseNo("10002")
					.build();
			ReflectionTestUtils.setField(intent1, "id", triggerIntentId);

			ExchangeIntentEntity intent2 = ExchangeIntentEntity.builder()
					.term(term).memberId(20L).giveCourseNo("10002").wantCourseNo("10003")
					.build();
			ReflectionTestUtils.setField(intent2, "id", 2L);

			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, triggerIntentId)))
					.willReturn(Optional.of(intent1));

			given(intentRepository.findByTermAndIsDeletedFalse(term))
					.willReturn(List.of(intent1, intent2));

			// When
			cycleDetector.detectCyclesAndCreateRooms(term, triggerIntentId, 10L, "10001", "10002");

			// Then
			verify(roomCreationService, never()).createRoom(any(), any(), any());
		}

		@Test
		@DisplayName("동일한 사이클 해시의 방이 존재하면 방 생성을 요청하지 않는다")
		void it_skips_when_room_already_exists() {
			// Given
			String term = "202510";
			Long triggerIntentId = 1L;

			ExchangeIntentEntity intent1 = ExchangeIntentEntity.builder()
					.term(term).memberId(10L).giveCourseNo("10001").wantCourseNo("10002")
					.build();
			ReflectionTestUtils.setField(intent1, "id", triggerIntentId);

			ExchangeIntentEntity intent2 = ExchangeIntentEntity.builder()
					.term(term).memberId(20L).giveCourseNo("10002").wantCourseNo("10001")
					.build();
			ReflectionTestUtils.setField(intent2, "id", 2L);

			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, triggerIntentId)))
					.willReturn(Optional.of(intent1));

			given(intentRepository.findByTermAndIsDeletedFalse(term))
					.willReturn(List.of(intent1, intent2));

			String cycleHash = cycleDetector.computeCycleHash(List.of(intent1, intent2));
			given(roomRepository.findByTermAndCycleHash(term, cycleHash))
					.willReturn(Optional.of(ExchangeRoomEntity.builder().build()));

			// When
			cycleDetector.detectCyclesAndCreateRooms(term, triggerIntentId, 10L, "10001", "10002");

			// Then
			verify(roomCreationService, never()).createRoom(any(), any(), any());
		}
	}
}
