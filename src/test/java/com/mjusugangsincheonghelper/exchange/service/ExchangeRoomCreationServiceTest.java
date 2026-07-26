package com.mjusugangsincheonghelper.exchange.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadStatusEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRoomCreationService 단위 테스트")
class ExchangeRoomCreationServiceTest {

	@Mock
	private EntityManager entityManager;

	@Mock
	private ExchangeRoomRepository roomRepository;

	@Mock
	private ExchangeRoomIntentRepository roomIntentRepository;

	@Mock
	private ExchangeRoomMessageRepository messageRepository;

	@Mock
	private ExchangeRoomReadStatusRepository readStatusRepository;

	@Mock
	private ExchangeCacheService cacheService;

	@InjectMocks
	private ExchangeRoomCreationService roomCreationService;

	@Nested
	@DisplayName("createRoom 메서드는")
	class Describe_createRoom {

		@Test
		@DisplayName("유효한 사이클 정보로 방을 성공적으로 생성한다")
		void it_creates_room_successfully() {
			// Given
			String term = "202620";
			String cycleHash = "hash123";

			ExchangeIntentEntity intent1 = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();
			ReflectionTestUtils.setField(intent1, "id", 10L);

			ExchangeIntentEntity intent2 = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(2L)
					.giveCourseNo("10002")
					.wantCourseNo("10001")
					.build();
			ReflectionTestUtils.setField(intent2, "id", 20L);

			List<ExchangeIntentEntity> cycle = List.of(intent1, intent2);

			given(entityManager.find(eq(ExchangeIntentEntity.class), any(), eq(LockModeType.PESSIMISTIC_WRITE)))
					.willReturn(intent1)
					.willReturn(intent2);

			given(roomRepository.findByTermAndCycleHash(term, cycleHash)).willReturn(Optional.empty());

			ExchangeRoomEntity savedRoom = ExchangeRoomEntity.builder()
					.term(term)
					.cycleHash(cycleHash)
					.status("ACTIVE")
					.isActive(true)
					.build();
			ReflectionTestUtils.setField(savedRoom, "id", 100L);

			given(roomRepository.save(any(ExchangeRoomEntity.class))).willReturn(savedRoom);

			ExchangeRoomMessageEntity savedMessage = ExchangeRoomMessageEntity.builder()
					.term(term)
					.roomId(100L)
					.memberId(1L)
					.intentId(10L)
					.content("Welcome message")
					.build();
			ReflectionTestUtils.setField(savedMessage, "id", 1000L);

			given(messageRepository.save(any(ExchangeRoomMessageEntity.class))).willReturn(savedMessage);

			ExchangeRoomReadStatusEntity readStatus1 = ExchangeRoomReadStatusEntity.builder()
					.term(term)
					.roomId(100L)
					.memberId(1L)
					.intentId(10L)
					.build();
			ExchangeRoomReadStatusEntity readStatus2 = ExchangeRoomReadStatusEntity.builder()
					.term(term)
					.roomId(100L)
					.memberId(2L)
					.intentId(20L)
					.build();

			given(readStatusRepository.findById(new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, 100L, 1L)))
					.willReturn(Optional.of(readStatus1));
			given(readStatusRepository.findById(new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, 100L, 2L)))
					.willReturn(Optional.of(readStatus2));

			// When
			Long roomId = roomCreationService.createRoom(term, cycle, cycleHash);

			// Then
			assertThat(roomId).isEqualTo(100L);
			verify(roomIntentRepository, times(2)).save(any(ExchangeRoomIntentEntity.class));
			verify(readStatusRepository, times(2)).save(any(ExchangeRoomReadStatusEntity.class));
			verify(cacheService).evictRooms(term, 1L);
			verify(cacheService).evictRooms(term, 2L);
			assertThat(readStatus1.getLastReadMessageId()).isEqualTo(1000L);
		}

		@Test
		@DisplayName("사이클 구성원 중 삭제된 의사가 있는 경우 방 생성을 건너뛴다")
		void it_skips_creation_when_intent_is_deleted() {
			// Given
			String term = "202620";
			String cycleHash = "hash123";

			ExchangeIntentEntity intent1 = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();
			ReflectionTestUtils.setField(intent1, "id", 10L);
			intent1.markDeleted(); // 삭제 처리

			List<ExchangeIntentEntity> cycle = List.of(intent1);

			given(entityManager.find(eq(ExchangeIntentEntity.class), any(), eq(LockModeType.PESSIMISTIC_WRITE)))
					.willReturn(intent1);

			// When
			Long roomId = roomCreationService.createRoom(term, cycle, cycleHash);

			// Then
			assertThat(roomId).isNull();
			verify(roomRepository, never()).save(any());
		}

		@Test
		@DisplayName("동일한 cycleHash를 가진 방이 이미 존재하면 방 생성을 건너뛴다")
		void it_skips_creation_when_room_already_exists() {
			// Given
			String term = "202620";
			String cycleHash = "hash123";

			ExchangeIntentEntity intent1 = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();
			ReflectionTestUtils.setField(intent1, "id", 10L);

			List<ExchangeIntentEntity> cycle = List.of(intent1);

			given(entityManager.find(eq(ExchangeIntentEntity.class), any(), eq(LockModeType.PESSIMISTIC_WRITE)))
					.willReturn(intent1);
			given(roomRepository.findByTermAndCycleHash(term, cycleHash))
					.willReturn(Optional.of(ExchangeRoomEntity.builder().build()));

			// When
			Long roomId = roomCreationService.createRoom(term, cycle, cycleHash);

			// Then
			assertThat(roomId).isNull();
			verify(roomRepository, never()).save(any());
		}

		@Test
		@DisplayName("방 생성 시 시스템 웰컴 메시지가 올바른 내용으로 저장된다")
		void it_creates_welcome_message_with_correct_content() {
			// Given
			String term = "202620";
			String cycleHash = "hash123";

			ExchangeIntentEntity intent1 = ExchangeIntentEntity.builder()
					.term(term).memberId(1L).giveCourseNo("10001").wantCourseNo("10002").build();
			ReflectionTestUtils.setField(intent1, "id", 10L);

			ExchangeIntentEntity intent2 = ExchangeIntentEntity.builder()
					.term(term).memberId(2L).giveCourseNo("10002").wantCourseNo("10001").build();
			ReflectionTestUtils.setField(intent2, "id", 20L);

			List<ExchangeIntentEntity> cycle = List.of(intent1, intent2);

			given(entityManager.find(eq(ExchangeIntentEntity.class), any(), eq(LockModeType.PESSIMISTIC_WRITE)))
					.willReturn(intent1)
					.willReturn(intent2);

			given(roomRepository.findByTermAndCycleHash(term, cycleHash)).willReturn(Optional.empty());

			ExchangeRoomEntity savedRoom = ExchangeRoomEntity.builder()
					.term(term).cycleHash(cycleHash).status("ACTIVE").isActive(true).build();
			ReflectionTestUtils.setField(savedRoom, "id", 100L);
			given(roomRepository.save(any(ExchangeRoomEntity.class))).willReturn(savedRoom);

			ExchangeRoomMessageEntity savedMessage = ExchangeRoomMessageEntity.builder()
					.term(term).roomId(100L).memberId(1L).intentId(10L).content("Welcome").build();
			ReflectionTestUtils.setField(savedMessage, "id", 1000L);
			given(messageRepository.save(any(ExchangeRoomMessageEntity.class))).willReturn(savedMessage);

			ExchangeRoomReadStatusEntity readStatus1 = ExchangeRoomReadStatusEntity.builder()
					.term(term).roomId(100L).memberId(1L).intentId(10L).build();
			ExchangeRoomReadStatusEntity readStatus2 = ExchangeRoomReadStatusEntity.builder()
					.term(term).roomId(100L).memberId(2L).intentId(20L).build();
			given(readStatusRepository.findById(new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, 100L, 1L)))
					.willReturn(Optional.of(readStatus1));
			given(readStatusRepository.findById(new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, 100L, 2L)))
					.willReturn(Optional.of(readStatus2));

			// When
			roomCreationService.createRoom(term, cycle, cycleHash);

			// Then
			ArgumentCaptor<ExchangeRoomMessageEntity> msgCaptor = ArgumentCaptor.forClass(ExchangeRoomMessageEntity.class);
			verify(messageRepository).save(msgCaptor.capture());
			ExchangeRoomMessageEntity savedMsg = msgCaptor.getValue();
			assertThat(savedMsg.getContent()).contains("[시스템] 교환 매칭이 성사되었습니다!");
			assertThat(savedMsg.getContent()).contains("10001");
			assertThat(savedMsg.getContent()).contains("10002");
		}
	}
}
