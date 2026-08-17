package com.mjusugangsincheonghelper.exchange.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendRequest;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse;
import com.mjusugangsincheonghelper.exchange.dto.RoomToggleRequest;
import com.mjusugangsincheonghelper.exchange.dto.RoomToggleResponse;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.IntentCacheDto;
import com.mjusugangsincheonghelper.exchange.event.ExchangeEvents;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeService 단위 테스트")
class ExchangeServiceTest {

	@Mock
	private ExchangeIntentRepository intentRepository;

	@Mock
	private ExchangeRoomIntentRepository roomIntentRepository;

	@Mock
	private ExchangeRoomRepository roomRepository;

	@Mock
	private ExchangeRoomMessageRepository messageRepository;

	@Mock
	private ExchangeRoomReadStatusRepository readStatusRepository;

	@Mock
	private ExchangeCacheService cacheService;

	@Mock
	private SystemConfigService systemConfigService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private ExchangeService exchangeService;

	@Nested
	@DisplayName("createIntent 메서드는")
	class Describe_createIntent {

		@Test
		@DisplayName("유효한 요청이면 교환 의사를 등록하고 응답을 반환한다")
		void it_creates_intent_and_returns_response() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			ExchangeIntentEntity savedEntity = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(memberId)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findByTermAndMemberIdAndGiveCourseNoAndWantCourseNoAndIsDeletedFalse(
					term, memberId, "10001", "10002")).willReturn(List.of());
			given(intentRepository.saveAndFlush(any(ExchangeIntentEntity.class))).willReturn(savedEntity);

			// When
			IntentCreateResponse response = exchangeService.createIntent(memberId, request);

			// Then
			assertThat(response.getIntentId()).isEqualTo(savedEntity.getId());
			assertThat(response.getMemberId()).isEqualTo(memberId);
			assertThat(response.getGiveCourseNo()).isEqualTo("10001");
			assertThat(response.getWantCourseNo()).isEqualTo("10002");
			verify(intentRepository).saveAndFlush(any(ExchangeIntentEntity.class));
		}

		@Test
		@DisplayName("giveCourseNo와 wantCourseNo가 같으면 예외를 발생시킨다")
		void it_throws_exception_when_same_course() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10001")
					.build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);

			// When & Then
			assertThatThrownBy(() -> exchangeService.createIntent(memberId, request))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_SAME_COURSE);
					});
		}

		@Test
		@DisplayName("중복된 의도가 있으면 예외를 발생시킨다")
		void it_throws_exception_when_duplicate() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			ExchangeIntentEntity existingEntity = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(memberId)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findByTermAndMemberIdAndGiveCourseNoAndWantCourseNoAndIsDeletedFalse(
					term, memberId, "10001", "10002")).willReturn(List.of(existingEntity));

			// When & Then
			assertThatThrownBy(() -> exchangeService.createIntent(memberId, request))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_DUPLICATE_INTENT);
					});
		}

		@Test
		@DisplayName("동시성으로 인해 DB 저장 시 중복 예외가 발생하면 EXCHANGE_DUPLICATE_INTENT 예외를 발생시킨다")
		void it_throws_duplicate_intent_exception_when_db_unique_violation_occurs() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findByTermAndMemberIdAndGiveCourseNoAndWantCourseNoAndIsDeletedFalse(
					term, memberId, "10001", "10002")).willReturn(List.of());
			given(intentRepository.saveAndFlush(any(ExchangeIntentEntity.class)))
					.willThrow(new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation"));

			// When & Then
			assertThatThrownBy(() -> exchangeService.createIntent(memberId, request))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_DUPLICATE_INTENT);
					});
		}

		@Test
		@DisplayName("등록 후 IntentCreated 이벤트가 발행된다 (커밋 후 캐시 evict·사이클 탐지 적재)")
		void it_publishes_intent_created_event() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			ExchangeIntentEntity savedEntity = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(memberId)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findByTermAndMemberIdAndGiveCourseNoAndWantCourseNoAndIsDeletedFalse(
					term, memberId, "10001", "10002")).willReturn(List.of());
			given(intentRepository.saveAndFlush(any(ExchangeIntentEntity.class))).willReturn(savedEntity);

			// When
			exchangeService.createIntent(memberId, request);

			// Then
			ArgumentCaptor<ExchangeEvents.IntentCreated> eventCaptor =
					ArgumentCaptor.forClass(ExchangeEvents.IntentCreated.class);
			verify(eventPublisher).publishEvent(eventCaptor.capture());
			ExchangeEvents.IntentCreated event = eventCaptor.getValue();
			assertThat(event.term()).isEqualTo(term);
			assertThat(event.memberId()).isEqualTo(memberId);
			assertThat(event.giveCourseNo()).isEqualTo("10001");
			assertThat(event.wantCourseNo()).isEqualTo("10002");
		}
	}

	@Nested
	@DisplayName("deleteIntent 메서드는")
	class Describe_deleteIntent {

		@Test
		@DisplayName("자신의 의사를 삭제할 수 있다")
		void it_deletes_own_intent() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long intentId = 100L;

			ExchangeIntentEntity intent = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(memberId)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentId)))
					.willReturn(Optional.of(intent));
			given(roomIntentRepository.findByTermAndIntentId(term, intentId)).willReturn(List.of());

			// When
			IntentDeleteResponse response = exchangeService.deleteIntent(memberId, intentId);

			// Then
			assertThat(response.getIntentId()).isEqualTo(intentId);
			assertThat(response.isDeleted()).isTrue();
			assertThat(intent.isDeleted()).isTrue();
		}

		@Test
		@DisplayName("존재하지 않는 의도면 예외를 발생시킨다")
		void it_throws_exception_when_not_found() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long intentId = 999L;

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentId)))
					.willReturn(Optional.empty());

			// When & Then
			assertThatThrownBy(() -> exchangeService.deleteIntent(memberId, intentId))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_INTENT_NOT_FOUND);
					});
		}

		@Test
		@DisplayName("다른 사용자의 의도면 예외를 발생시킨다")
		void it_throws_exception_when_not_owner() {
			// Given
			String term = "202620";
			Long ownerId = 1L;
			Long requesterId = 2L;
			Long intentId = 100L;

			ExchangeIntentEntity intent = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(ownerId)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentId)))
					.willReturn(Optional.of(intent));

			// When & Then
			assertThatThrownBy(() -> exchangeService.deleteIntent(requesterId, intentId))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_INTENT_NOT_OWNER);
					});
		}

		@Test
		@DisplayName("이미 삭제된 의도면 예외를 발생시킨다")
		void it_throws_exception_when_already_deleted() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long intentId = 100L;

			ExchangeIntentEntity intent = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(memberId)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();
			intent.markDeleted();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentId)))
					.willReturn(Optional.of(intent));

			// When & Then
			assertThatThrownBy(() -> exchangeService.deleteIntent(memberId, intentId))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED);
					});
		}

		@Test
		@DisplayName("의사 철회 시 연관된 RoomIntent에 markDeleted가 호출된다")
		void it_cascades_mark_deleted_to_room_intents() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long intentId = 100L;
			Long roomId = 200L;

			ExchangeIntentEntity intent = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(memberId)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			ExchangeRoomIntentEntity roomIntent = ExchangeRoomIntentEntity.builder()
					.term(term)
					.roomId(roomId)
					.intentId(intentId)
					.memberId(memberId)
					.build();

			ExchangeRoomEntity room = ExchangeRoomEntity.builder()
					.term(term)
					.cycleHash("hash")
					.status("ACTIVE")
					.build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentId)))
					.willReturn(Optional.of(intent));
			given(roomIntentRepository.findByTermAndIntentId(term, intentId))
					.willReturn(List.of(roomIntent));
			given(roomIntentRepository.findByTermAndRoomId(term, roomId))
					.willReturn(List.of(roomIntent));
			given(roomRepository.findByIdForUpdate(term, roomId))
					.willReturn(Optional.of(room));

			// When
			exchangeService.deleteIntent(memberId, intentId);

			// Then
			assertThat(roomIntent.isDeleted()).isTrue();
			assertThat(roomIntent.isOn()).isFalse();
		}

		@Test
		@DisplayName("2인 방에서 1명 이탈 시 방 상태가 PARTIAL_DELETE로 전이된다")
		void it_transitions_to_partial_delete_when_2_person_room_loses_member() {
			// Given
			String term = "202620";
			Long memberIdA = 1L;
			Long memberIdB = 2L;
			Long intentIdA = 100L;
			Long roomId = 200L;

			ExchangeIntentEntity intentA = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(memberIdA)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			ExchangeRoomIntentEntity riA = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).intentId(intentIdA).memberId(memberIdA).build();
			ExchangeRoomIntentEntity riB = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).intentId(200L).memberId(memberIdB).build();

			ExchangeRoomEntity room = ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash").status("ACTIVE").build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentIdA)))
					.willReturn(Optional.of(intentA));
			given(roomIntentRepository.findByTermAndIntentId(term, intentIdA))
					.willReturn(List.of(riA));
			given(roomIntentRepository.findByTermAndRoomId(term, roomId))
					.willReturn(List.of(riA, riB));
			given(roomRepository.findByIdForUpdate(term, roomId))
					.willReturn(Optional.of(room));

			// When
			exchangeService.deleteIntent(memberIdA, intentIdA);

			// Then
			assertThat(room.getStatus()).isEqualTo("PARTIAL_DELETE");
		}

		@Test
		@DisplayName("3인 방에서 1명 이탈 시 방 상태가 PARTIAL_DELETE로 전이된다")
		void it_transitions_to_partial_delete_when_3_person_room_loses_member() {
			// Given
			String term = "202620";
			Long memberIdA = 1L;
			Long memberIdB = 2L;
			Long memberIdC = 3L;
			Long intentIdA = 100L;
			Long roomId = 200L;

			ExchangeIntentEntity intentA = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(memberIdA)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			ExchangeRoomIntentEntity riA = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).intentId(intentIdA).memberId(memberIdA).build();
			ExchangeRoomIntentEntity riB = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).intentId(200L).memberId(memberIdB).build();
			ExchangeRoomIntentEntity riC = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).intentId(300L).memberId(memberIdC).build();

			ExchangeRoomEntity room = ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash").status("ACTIVE").build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentIdA)))
					.willReturn(Optional.of(intentA));
			given(roomIntentRepository.findByTermAndIntentId(term, intentIdA))
					.willReturn(List.of(riA));
			given(roomIntentRepository.findByTermAndRoomId(term, roomId))
					.willReturn(List.of(riA, riB, riC));
			given(roomRepository.findByIdForUpdate(term, roomId))
					.willReturn(Optional.of(room));

			// When
			exchangeService.deleteIntent(memberIdA, intentIdA);

			// Then
			assertThat(room.getStatus()).isEqualTo("PARTIAL_DELETE");
		}

		@Test
		@DisplayName("전원 이탈 시 방 상태가 ALL_DELETE로 전이되고 시스템 메시지가 저장된다")
		void it_saves_system_message_on_all_delete() {
			// Given
			String term = "202620";
			Long memberIdA = 1L;
			Long intentIdA = 100L;
			Long roomId = 200L;

			ExchangeIntentEntity intentA = ExchangeIntentEntity.builder()
					.term(term).memberId(memberIdA).giveCourseNo("10001").wantCourseNo("10002").build();

			ExchangeRoomIntentEntity riA = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).intentId(intentIdA).memberId(memberIdA).build();

			ExchangeRoomEntity room = ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash").status("PARTIAL_DELETE").build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentIdA)))
					.willReturn(Optional.of(intentA));
			given(roomIntentRepository.findByTermAndIntentId(term, intentIdA))
					.willReturn(List.of(riA));
			given(roomIntentRepository.findByTermAndRoomId(term, roomId))
					.willReturn(List.of(riA));
			given(roomRepository.findByIdForUpdate(term, roomId))
					.willReturn(Optional.of(room));

			// When
			exchangeService.deleteIntent(memberIdA, intentIdA);

			// Then
			assertThat(room.getStatus()).isEqualTo("ALL_DELETE");
			verify(messageRepository).save(any(ExchangeRoomMessageEntity.class));
		}

		@Test
		@DisplayName("삭제 후 IntentDeleted 이벤트에 영향받은 방·참여자가 담긴다")
		void it_publishes_intent_deleted_event() {
			// Given
			String term = "202620";
			Long memberIdA = 1L;
			Long memberIdB = 2L;
			Long intentIdA = 100L;
			Long roomId = 200L;

			ExchangeIntentEntity intentA = ExchangeIntentEntity.builder()
					.term(term).memberId(memberIdA).giveCourseNo("10001").wantCourseNo("10002").build();

			ExchangeRoomIntentEntity riA = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).intentId(intentIdA).memberId(memberIdA).build();
			ExchangeRoomIntentEntity riB = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).intentId(200L).memberId(memberIdB).build();

			ExchangeRoomEntity room = ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash").status("ACTIVE").build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentIdA)))
					.willReturn(Optional.of(intentA));
			given(roomIntentRepository.findByTermAndIntentId(term, intentIdA))
					.willReturn(List.of(riA, riB));
			given(roomIntentRepository.findByTermAndRoomId(term, roomId))
					.willReturn(List.of(riA, riB));
			given(roomRepository.findByIdForUpdate(term, roomId))
					.willReturn(Optional.of(room));

			// When
			exchangeService.deleteIntent(memberIdA, intentIdA);

			// Then
			ArgumentCaptor<ExchangeEvents.IntentDeleted> eventCaptor =
					ArgumentCaptor.forClass(ExchangeEvents.IntentDeleted.class);
			verify(eventPublisher).publishEvent(eventCaptor.capture());
			ExchangeEvents.IntentDeleted event = eventCaptor.getValue();
			assertThat(event.term()).isEqualTo(term);
			assertThat(event.memberId()).isEqualTo(memberIdA);
			assertThat(event.roomIds()).containsExactly(roomId);
			assertThat(event.memberIds()).containsExactlyInAnyOrder(memberIdA, memberIdB);
		}
	}

	@Nested
	@DisplayName("getMain 메서드는")
	class Describe_getMain {

		@Test
		@DisplayName("사용자의 의도와 방 목록, 최근 피드를 조회하여 반환한다")
		void it_returns_main_response() {
			// Given
			String term = "202620";
			Long memberId = 1L;

			IntentCacheDto intent = IntentCacheDto.builder()
					.intentId(10L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.createdAt(java.time.Instant.now())
					.build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(cacheService.getStoredMain(term, memberId)).willReturn(null);
			given(cacheService.computeMemberIntents(term, memberId)).willReturn(List.of(intent));
			given(roomIntentRepository.findByTermAndIntentId(term, 10L)).willReturn(List.of());
			given(cacheService.getFeed(term)).willReturn(List.of(
					FeedCacheDto.builder()
							.intentId(20L)
							.giveCourseNo("10003")
							.wantCourseNo("10004")
							.createdAt(java.time.Instant.now())
							.build()
			));

			// When
			MainResponse response = exchangeService.getMain(memberId);

			// Then
			assertThat(response.getMyIntents()).hasSize(1);
			assertThat(response.getMyIntents().get(0).getIntentId()).isEqualTo(10L);
			assertThat(response.getRecentIntents()).hasSize(1);
			assertThat(response.getRecentIntents().get(0).getIntentId()).isEqualTo(20L);
		}
	}

	@Nested
	@DisplayName("getRecentIntents 메서드는")
	class Describe_getRecentIntents {

		@Test
		@DisplayName("최근 교환 의도 목록을 반환한다")
		void it_returns_recent_intents() {
			// Given
			String term = "202620";

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(cacheService.getFeed(term)).willReturn(List.of(
					FeedCacheDto.builder()
							.intentId(15L)
							.giveCourseNo("10001")
							.wantCourseNo("10002")
							.createdAt(java.time.Instant.now())
							.build()
			));

			// When
			RecentIntentsResponse response = exchangeService.getRecentIntents();

			// Then
			assertThat(response.getRecentIntents()).hasSize(1);
			assertThat(response.getRecentIntents().get(0).getIntentId()).isEqualTo(15L);
		}
	}

	@Nested
	@DisplayName("getMessages 메서드는")
	class Describe_getMessages {

		@Test
		@DisplayName("방 참여자가 아니면 예외를 발생시킨다")
		void it_throws_exception_when_not_member() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long roomId = 100L;

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId)).willReturn(List.of());

			// When & Then
			assertThatThrownBy(() -> exchangeService.getMessages(memberId, roomId, null, 20))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER);
					});
		}

		@Test
		@DisplayName("유효한 방 참여자면 메시지 내역을 반환하고 읽음 처리한다")
		void it_returns_messages_and_updates_read_status() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long roomId = 100L;

			ExchangeRoomIntentEntity roomIntent = 
					ExchangeRoomIntentEntity.builder()
							.term(term)
							.roomId(roomId)
							.memberId(memberId)
							.intentId(50L)
							.build();

			ExchangeRoomMessageEntity message =
					ExchangeRoomMessageEntity.builder()
							.term(term)
							.roomId(roomId)
							.memberId(memberId)
							.intentId(50L)
							.content("Test message")
							.build();
			ReflectionTestUtils.setField(message, "id", 1000L);

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId)).willReturn(List.of(roomIntent));
			given(messageRepository.findByTermAndRoomIdOrderByIdDesc(eq(term), eq(roomId), any(org.springframework.data.domain.Pageable.class)))
					.willReturn(List.of(message));
			given(readStatusRepository.findById(any())).willReturn(Optional.empty());

			// When
			MessageResponse response = exchangeService.getMessages(memberId, roomId, null, 20);

			// Then
			assertThat(response.getRoomId()).isEqualTo(roomId);
			assertThat(response.getMessages()).hasSize(1);
			assertThat(response.getMessages().get(0).getContent()).isEqualTo("Test message");
			verify(readStatusRepository).save(any());
			verify(eventPublisher).publishEvent(any(ExchangeEvents.RoomViewed.class));
		}
	}

	@Nested
	@DisplayName("sendMessage 메서드는")
	class Describe_sendMessage {

		@Test
		@DisplayName("방 멤버가 아니면 예외를 발생시킨다")
		void it_throws_exception_when_not_member() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long roomId = 100L;
			MessageSendRequest request = MessageSendRequest.builder().content("Hello").build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId)).willReturn(List.of());

			// When & Then
			assertThatThrownBy(() -> exchangeService.sendMessage(memberId, roomId, request))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER);
					});
		}

		@Test
		@DisplayName("방 멤버이지만 활성화된 의도가 없으면 예외를 발생시킨다")
		void it_throws_exception_when_intent_already_deleted() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long roomId = 100L;
			MessageSendRequest request = MessageSendRequest.builder().content("Hello").build();

			ExchangeRoomIntentEntity roomIntent = 
					ExchangeRoomIntentEntity.builder()
							.term(term)
							.roomId(roomId)
							.memberId(memberId)
							.intentId(50L)
							.build();
			roomIntent.markDeleted();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId)).willReturn(List.of(roomIntent));

			// When & Then
			assertThatThrownBy(() -> exchangeService.sendMessage(memberId, roomId, request))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED);
					});
		}

		@Test
		@DisplayName("유효한 방 멤버이면 메시지를 전송하고 main:cache를 만료시킨다")
		void it_sends_message() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long roomId = 100L;
			MessageSendRequest request = MessageSendRequest.builder().content("Hello").build();

			ExchangeRoomIntentEntity roomIntent1 = 
					ExchangeRoomIntentEntity.builder()
							.term(term)
							.roomId(roomId)
							.memberId(memberId)
							.intentId(50L)
							.build();
			ExchangeRoomIntentEntity roomIntent2 = 
					ExchangeRoomIntentEntity.builder()
							.term(term)
							.roomId(roomId)
							.memberId(2L)
							.intentId(51L)
							.build();

			ExchangeRoomMessageEntity message =
					ExchangeRoomMessageEntity.builder()
							.term(term)
							.roomId(roomId)
							.memberId(memberId)
							.intentId(50L)
							.messageType("TALK")
							.content("Hello")
							.build();
			ReflectionTestUtils.setField(message, "id", 1000L);

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId)).willReturn(List.of(roomIntent1));
			given(roomIntentRepository.findByTermAndRoomId(term, roomId)).willReturn(List.of(roomIntent1, roomIntent2));
			given(messageRepository.save(any())).willReturn(message);
			given(readStatusRepository.findById(any())).willReturn(Optional.empty());

			// When
			MessageSendResponse response = exchangeService.sendMessage(memberId, roomId, request);

			// Then
			assertThat(response.getMessageId()).isEqualTo(1000L);
			assertThat(response.getContent()).isEqualTo("Hello");
			verify(messageRepository).save(any());
			ArgumentCaptor<ExchangeEvents.RoomMessageSent> eventCaptor =
					ArgumentCaptor.forClass(ExchangeEvents.RoomMessageSent.class);
			verify(eventPublisher).publishEvent(eventCaptor.capture());
			ExchangeEvents.RoomMessageSent event = eventCaptor.getValue();
			assertThat(event.term()).isEqualTo(term);
			assertThat(event.roomId()).isEqualTo(roomId);
			assertThat(event.senderMemberId()).isEqualTo(memberId);
			assertThat(event.content()).isEqualTo("Hello");
		}
	}

	@Nested
	@DisplayName("toggleRoom 메서드는")
	class Describe_toggleRoom {

		@Test
		@DisplayName("방 멤버가 아니면 예외를 발생시킨다")
		void it_throws_exception_when_not_member() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long roomId = 100L;
			RoomToggleRequest request = RoomToggleRequest.builder().isOn(false).build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId)).willReturn(List.of());

			// When & Then
			assertThatThrownBy(() -> exchangeService.toggleRoom(memberId, roomId, request))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER);
					});
		}

		@Test
		@DisplayName("방 멤버이면 상태를 토글하고 main:cache를 만료시킨다")
		void it_toggles_room_status() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			Long roomId = 100L;
			RoomToggleRequest request = RoomToggleRequest.builder().isOn(false).build();

			ExchangeRoomIntentEntity roomIntent = 
					ExchangeRoomIntentEntity.builder()
							.term(term)
							.roomId(roomId)
							.memberId(memberId)
							.intentId(50L)
							.build();

			ExchangeRoomEntity room = ExchangeRoomEntity.builder()
					.term(term)
					.cycleHash("hash")
					.status("ACTIVE")
					.build();
			ReflectionTestUtils.setField(room, "id", roomId);

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId)).willReturn(List.of(roomIntent));
			given(roomIntentRepository.findByTermAndRoomId(term, roomId)).willReturn(List.of(roomIntent));
			given(roomRepository.findByIdForUpdate(term, roomId)).willReturn(Optional.of(room));

			// When
			RoomToggleResponse response = exchangeService.toggleRoom(memberId, roomId, request);

			// Then
			assertThat(response.getRoomId()).isEqualTo(roomId);
			assertThat(response.isOn()).isFalse();
			assertThat(roomIntent.isOn()).isFalse();
			verify(eventPublisher).publishEvent(any(ExchangeEvents.RoomToggled.class));
		}

		@Test
		@DisplayName("토글 OFF 시 삭제된 카드가 없고 일부가 OFF이면 방 상태가 PARTIAL_OFF로 전이된다")
		void it_transitions_to_partial_off_on_toggle_off() {
			// Given
			String term = "202620";
			Long memberIdA = 1L;
			Long memberIdB = 2L;
			Long roomId = 100L;
			RoomToggleRequest request = RoomToggleRequest.builder().isOn(false).build();

			ExchangeRoomIntentEntity riA = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).memberId(memberIdA).intentId(50L).build();
			ExchangeRoomIntentEntity riB = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).memberId(memberIdB).intentId(60L).build();

			ExchangeRoomEntity room = ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash").status("ACTIVE").build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberIdA)).willReturn(List.of(riA));
			given(roomIntentRepository.findByTermAndRoomId(term, roomId)).willReturn(List.of(riA, riB));
			given(roomRepository.findByIdForUpdate(term, roomId)).willReturn(Optional.of(room));

			// When
			exchangeService.toggleRoom(memberIdA, roomId, request);

			// Then
			assertThat(room.getStatus()).isEqualTo("PARTIAL_OFF");
		}

		@Test
		@DisplayName("토글 ON 복귀 시 삭제된 카드도 없고 모두 ON이면 방 상태가 ACTIVE로 전이된다")
		void it_transitions_to_active_on_toggle_on() {
			// Given
			String term = "202620";
			Long memberIdA = 1L;
			Long memberIdB = 2L;
			Long roomId = 100L;
			RoomToggleRequest request = RoomToggleRequest.builder().isOn(true).build();

			ExchangeRoomIntentEntity riA = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).memberId(memberIdA).intentId(50L).build();
			riA.toggle(false);
			ExchangeRoomIntentEntity riB = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(roomId).memberId(memberIdB).intentId(60L).build();

			ExchangeRoomEntity room = ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash").status("PARTIAL_OFF").build();

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberIdA)).willReturn(List.of(riA));
			given(roomIntentRepository.findByTermAndRoomId(term, roomId)).willReturn(List.of(riA, riB));
			given(roomRepository.findByIdForUpdate(term, roomId)).willReturn(Optional.of(room));

			// When
			exchangeService.toggleRoom(memberIdA, roomId, request);

			// Then
			assertThat(room.getStatus()).isEqualTo("ACTIVE");
		}
	}
}
