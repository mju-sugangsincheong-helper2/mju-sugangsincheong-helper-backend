package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity.ExchangeIntentId;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMemberRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadRepository;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.exchange.dto.cache.IntentDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomActiveIntentsDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomDynamicMetaDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomStaticMetaDto;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExchangeService 단위 테스트")
class ExchangeServiceTest {

	@Mock
	private ExchangeIntentRepository intentRepository;

	@Mock
	private ExchangeRoomMemberRepository roomMemberRepository;

	@Mock
	private ExchangeMessageRepository messageRepository;

	@Mock
	private ExchangeRoomReadRepository roomReadRepository;

	@Mock
	private ExchangeCycleDetector cycleDetector;

	@Mock
	private ExchangeUserCacheService userCacheService;

	@Mock
	private ExchangeRoomCacheService roomCacheService;

	@Mock
	private ExchangePageCacheService pageCacheService;

	@Mock
	private CacheManager cacheManager;

	@Mock
	private SystemConfigService systemConfigService;

	@InjectMocks
	private ExchangeService exchangeService;

	private static final String TERM = "202510";
	private static final Long MEMBER_ID = 100L;

	@BeforeEach
	void setUp() {
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

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
	@DisplayName("createIntent 메서드는")
	class Describe_createIntent {

		@Test
		@DisplayName("유효한 요청으로 의도를 생성한다")
		void it_creates_intent_with_valid_request() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			ExchangeIntentEntity savedEntity = createIntent(1L, MEMBER_ID, "10001", "10002");

			given(userCacheService.getUserIntents(TERM, MEMBER_ID))
					.willReturn(Collections.emptyList());
			given(intentRepository.save(any(ExchangeIntentEntity.class)))
					.willReturn(savedEntity);

			IntentCreateResponse response = exchangeService.createIntent(MEMBER_ID, request);

			assertThat(response.getIntentId()).isEqualTo(1L);
			assertThat(response.getGiveCourseNo()).isEqualTo("10001");
			assertThat(response.getWantCourseNo()).isEqualTo("10002");
			assertThat(response.isDeleted()).isFalse();
		}

		@Test
		@DisplayName("giveCourseNo와 wantCourseNo가 같으면 예외를 발생시킨다")
		void it_throws_when_same_course() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10001")
					.build();

			assertThatThrownBy(() -> exchangeService.createIntent(MEMBER_ID, request))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCHANGE_SAME_COURSE);
		}

		@Test
		@DisplayName("중복된 의도가 있으면 예외를 발생시킨다")
		void it_throws_when_duplicate_intent() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			IntentDto existingIntent = IntentDto.builder()
					.intentId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.isDeleted(false)
					.createdAt(java.time.Instant.now())
					.build();

			given(userCacheService.getUserIntents(TERM, MEMBER_ID))
					.willReturn(List.of(existingIntent));

			assertThatThrownBy(() -> exchangeService.createIntent(MEMBER_ID, request))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCHANGE_DUPLICATE_INTENT);
		}

		@Test
		@DisplayName("이미 삭제된 의도는 중복 체크에서 제외한다")
		void it_ignores_deleted_intents_for_duplicate_check() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			IntentDto deletedIntent = IntentDto.builder()
					.intentId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.isDeleted(true)
					.createdAt(java.time.Instant.now())
					.build();

			ExchangeIntentEntity savedEntity = createIntent(2L, MEMBER_ID, "10001", "10002");

			given(userCacheService.getUserIntents(TERM, MEMBER_ID))
					.willReturn(List.of(deletedIntent));
			given(intentRepository.save(any(ExchangeIntentEntity.class)))
					.willReturn(savedEntity);

			IntentCreateResponse response = exchangeService.createIntent(MEMBER_ID, request);

			assertThat(response.getIntentId()).isEqualTo(2L);
		}
	}

	@Nested
	@DisplayName("deleteIntent 메서드는")
	class Describe_deleteIntent {

		@Test
		@DisplayName("자신의 의도를 성공적으로 삭제한다")
		void it_deletes_own_intent() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);
			Long intentId = 1L;
			ExchangeIntentEntity intent = createIntent(intentId, MEMBER_ID, "10001", "10002");

			given(intentRepository.findById(new ExchangeIntentId(TERM, intentId)))
					.willReturn(Optional.of(intent));
			given(roomMemberRepository.findByTermAndMemberId(TERM, MEMBER_ID))
					.willReturn(Collections.emptyList());

			Cache mockCache = org.mockito.Mockito.mock(Cache.class);
			given(cacheManager.getCache(anyString())).willReturn(mockCache);

			IntentDeleteResponse response = exchangeService.deleteIntent(MEMBER_ID, intentId);

			assertThat(response.getIntentId()).isEqualTo(intentId);
			assertThat(response.isDeleted()).isTrue();
			assertThat(intent.isDeleted()).isTrue();
		}

		@Test
		@DisplayName("존재하지 않는 의도이면 예외를 발생시킨다")
		void it_throws_when_intent_not_found() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);
			Long intentId = 999L;

			given(intentRepository.findById(new ExchangeIntentId(TERM, intentId)))
					.willReturn(Optional.empty());

			assertThatThrownBy(() -> exchangeService.deleteIntent(MEMBER_ID, intentId))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCHANGE_INTENT_NOT_FOUND);
		}

		@Test
		@DisplayName("다른 사용자의 의도이면 예외를 발생시킨다")
		void it_throws_when_not_owner() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);
			Long intentId = 1L;
			Long otherMemberId = 999L;
			ExchangeIntentEntity intent = createIntent(intentId, otherMemberId, "10001", "10002");

			given(intentRepository.findById(new ExchangeIntentId(TERM, intentId)))
					.willReturn(Optional.of(intent));

			assertThatThrownBy(() -> exchangeService.deleteIntent(MEMBER_ID, intentId))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCHANGE_INTENT_NOT_OWNER);
		}

		@Test
		@DisplayName("이미 삭제된 의도이면 예외를 발생시킨다")
		void it_throws_when_already_deleted() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);
			Long intentId = 1L;
			ExchangeIntentEntity intent = createIntent(intentId, MEMBER_ID, "10001", "10002");
			intent.delete();

			given(intentRepository.findById(new ExchangeIntentId(TERM, intentId)))
					.willReturn(Optional.of(intent));

			assertThatThrownBy(() -> exchangeService.deleteIntent(MEMBER_ID, intentId))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED);
		}
	}

	@Nested
	@DisplayName("getMain 메서드는")
	class Describe_getMain {

		@Test
		@DisplayName("나의 의도 목록과 방 목록을 반환한다")
		void it_returns_my_intents_and_rooms() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);

			IntentDto intent = IntentDto.builder()
					.intentId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.isDeleted(false)
					.createdAt(java.time.Instant.now())
					.build();

			given(userCacheService.getUserIntents(TERM, MEMBER_ID))
					.willReturn(List.of(intent));
			given(userCacheService.getUserRoomIds(TERM, MEMBER_ID))
					.willReturn(Collections.emptyList());
			given(userCacheService.getUserUnreadCounts(TERM, MEMBER_ID))
					.willReturn(Collections.emptyMap());

			var response = exchangeService.getMain(MEMBER_ID);

			assertThat(response.getMyIntents()).hasSize(1);
			assertThat(response.getMyIntents().get(0).getIntentId()).isEqualTo(1L);
			assertThat(response.getMyRooms()).isEmpty();
		}

		@Test
		@DisplayName("방 정보를 마이크로 캐시에서 조립한다")
		void it_assembles_rooms_from_micro_caches() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);

			given(userCacheService.getUserIntents(TERM, MEMBER_ID))
					.willReturn(Collections.emptyList());
			given(userCacheService.getUserRoomIds(TERM, MEMBER_ID))
					.willReturn(List.of(1L));
			given(userCacheService.getUserUnreadCounts(TERM, MEMBER_ID))
					.willReturn(java.util.Map.of(1L, 3));

			RoomStaticMetaDto staticMeta = RoomStaticMetaDto.builder()
					.roomId(1L)
					.totalParticipants(3)
					.cycleDetails(Collections.emptyList())
					.build();
			given(roomCacheService.getRoomStaticMeta(TERM, 1L)).willReturn(staticMeta);

			RoomDynamicMetaDto dynamicMeta = RoomDynamicMetaDto.builder()
					.lastMessage("test")
					.lastMessageAt(java.time.Instant.now())
					.build();
			given(roomCacheService.getRoomDynamicMeta(TERM, 1L)).willReturn(dynamicMeta);

			RoomActiveIntentsDto activeIntents = RoomActiveIntentsDto.builder()
					.intents(List.of(
							RoomActiveIntentsDto.ActiveIntent.builder().intentId(1L).memberId(100L).isDeleted(false).build(),
							RoomActiveIntentsDto.ActiveIntent.builder().intentId(2L).memberId(200L).isDeleted(false).build(),
							RoomActiveIntentsDto.ActiveIntent.builder().intentId(3L).memberId(300L).isDeleted(false).build()
					))
					.build();
			given(roomCacheService.getRoomActiveIntents(TERM, 1L)).willReturn(activeIntents);

			var response = exchangeService.getMain(MEMBER_ID);

			assertThat(response.getMyRooms()).hasSize(1);
			assertThat(response.getMyRooms().get(0).getRoomId()).isEqualTo(1L);
			assertThat(response.getMyRooms().get(0).getTotalParticipants()).isEqualTo(3);
			assertThat(response.getMyRooms().get(0).getActiveIntentCount()).isEqualTo(3);
			assertThat(response.getMyRooms().get(0).getUnreadMessageCount()).isEqualTo(3);
		}
	}
}
