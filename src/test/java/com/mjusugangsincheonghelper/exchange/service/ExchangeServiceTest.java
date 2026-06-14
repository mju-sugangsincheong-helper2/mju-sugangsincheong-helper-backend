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
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
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
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomCacheDto;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
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
	private ExchangeCycleDetector cycleDetector;

	@Mock
	private ExchangeCacheService cacheService;

	@Mock
	private SystemConfigService systemConfigService;

	@InjectMocks
	private ExchangeService exchangeService;

	@Nested
	@DisplayName("createIntent 메서드는")
	class Describe_createIntent {

		@Test
		@DisplayName("유효한 요청이면 교환 의사를 등록하고 응답을 반환한다")
		void it_creates_intent_and_returns_response() {
			// Given
			String term = "202510";
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
			given(intentRepository.save(any(ExchangeIntentEntity.class))).willReturn(savedEntity);

			// When
			IntentCreateResponse response = exchangeService.createIntent(memberId, request);

			// Then
			assertThat(response.getIntentId()).isEqualTo(savedEntity.getId());
			assertThat(response.getMemberId()).isEqualTo(memberId);
			assertThat(response.getGiveCourseNo()).isEqualTo("10001");
			assertThat(response.getWantCourseNo()).isEqualTo("10002");
			verify(intentRepository).save(any(ExchangeIntentEntity.class));
		}

		@Test
		@DisplayName("giveCourseNo와 wantCourseNo가 같으면 예외를 발생시킨다")
		void it_throws_exception_when_same_course() {
			// Given
			String term = "202510";
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
			String term = "202510";
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
	}

	@Nested
	@DisplayName("deleteIntent 메서드는")
	class Describe_deleteIntent {

		@Test
		@DisplayName("자신의 의사를 삭제할 수 있다")
		void it_deletes_own_intent() {
			// Given
			String term = "202510";
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
			String term = "202510";
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
			String term = "202510";
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
			String term = "202510";
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
	}

	@Nested
	@DisplayName("getMain 메서드는")
	class Describe_getMain {

		@Test
		@DisplayName("사용자의 의도와 방 목록, 최근 피드를 조회하여 반환한다")
		void it_returns_main_response() {
			// Given
			String term = "202510";
			Long memberId = 1L;

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(cacheService.getIntents(term, memberId)).willReturn(List.of(
					IntentCacheDto.builder()
							.intentId(10L)
							.giveCourseNo("10001")
							.wantCourseNo("10002")
							.isDeleted(false)
							.build()
			));
			given(cacheService.getRooms(term, memberId)).willReturn(List.of(
					RoomCacheDto.builder()
							.roomId(100L)
							.isActive(true)
							.isOn(true)
							.unreadCount(2)
							.lastMessageContent("Hello")
							.lastMessageAt(java.time.Instant.now())
							.build()
			));
			given(cacheService.getFeedSlice(term, null, 5)).willReturn(List.of(
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
			assertThat(response.getMyRooms()).hasSize(1);
			assertThat(response.getMyRooms().get(0).getRoomId()).isEqualTo(100L);
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
			String term = "202510";
			Long lastIntentId = 0L;
			int limit = 10;

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(cacheService.getFeedSlice(term, lastIntentId, limit + 1)).willReturn(List.of(
					FeedCacheDto.builder()
							.intentId(15L)
							.giveCourseNo("10001")
							.wantCourseNo("10002")
							.createdAt(java.time.Instant.now())
							.build()
			));

			// When
			RecentIntentsResponse response = exchangeService.getRecentIntents(lastIntentId, limit);

			// Then
			assertThat(response.getIntents()).hasSize(1);
			assertThat(response.getIntents().get(0).getIntentId()).isEqualTo(15L);
			assertThat(response.isHasNext()).isFalse();
		}
	}

	@Nested
	@DisplayName("getMessages 메서드는")
	class Describe_getMessages {

		@Test
		@DisplayName("방 참여자가 아니면 예외를 발생시킨다")
		void it_throws_exception_when_not_member() {
			// Given
			String term = "202510";
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
			String term = "202510";
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
		}
	}

	@Nested
	@DisplayName("sendMessage 메서드는")
	class Describe_sendMessage {

		@Test
		@DisplayName("방 멤버가 아니면 예외를 발생시킨다")
		void it_throws_exception_when_not_member() {
			// Given
			String term = "202510";
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
			String term = "202510";
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
		@DisplayName("유효한 방 멤버이면 메시지를 전송하고 방 목록 캐시를 만료시킨다")
		void it_sends_message() {
			// Given
			String term = "202510";
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

			ExchangeRoomMessageEntity message =
					ExchangeRoomMessageEntity.builder()
							.term(term)
							.roomId(roomId)
							.memberId(memberId)
							.intentId(50L)
							.content("Hello")
							.build();
			ReflectionTestUtils.setField(message, "id", 1000L);

			given(systemConfigService.getCurrentTerm()).willReturn(term);
			given(roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId)).willReturn(List.of(roomIntent));
			given(messageRepository.save(any())).willReturn(message);
			given(readStatusRepository.findById(any())).willReturn(Optional.empty());
			given(roomIntentRepository.findDistinctMemberIdsByTermAndRoomId(term, roomId)).willReturn(List.of(1L, 2L));

			// When
			MessageSendResponse response = exchangeService.sendMessage(memberId, roomId, request);

			// Then
			assertThat(response.getMessageId()).isEqualTo(1000L);
			assertThat(response.getContent()).isEqualTo("Hello");
			verify(messageRepository).save(any());
			verify(cacheService).evictRooms(term, 1L);
			verify(cacheService).evictRooms(term, 2L);
		}
	}

	@Nested
	@DisplayName("toggleRoom 메서드는")
	class Describe_toggleRoom {

		@Test
		@DisplayName("방 멤버가 아니면 예외를 발생시킨다")
		void it_throws_exception_when_not_member() {
			// Given
			String term = "202510";
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
		@DisplayName("방 멤버이면 상태를 토글하고 캐시를 만료시킨다")
		void it_toggles_room_status() {
			// Given
			String term = "202510";
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
					.isActive(true)
					.build();
			org.springframework.test.util.ReflectionTestUtils.setField(room, "id", roomId);

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
			verify(cacheService).evictRooms(term, memberId);
		}
	}
}
