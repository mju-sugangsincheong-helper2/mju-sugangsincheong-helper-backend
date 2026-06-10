package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity.ExchangeIntentId;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMemberRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExchangeService 단위 테스트")
class ExchangeServiceTest {

	@Mock
	private ExchangeIntentRepository intentRepository;

	@Mock
	private ExchangeRoomRepository roomRepository;

	@Mock
	private ExchangeRoomMemberRepository roomMemberRepository;

	@Mock
	private ExchangeMessageRepository messageRepository;

	@Mock
	private ExchangeRoomReadRepository roomReadRepository;

	@Mock
	private ExchangeCycleDetector cycleDetector;

	@Mock
	private ExchangeRedisService redisService;

	@Mock
	private SystemConfigService systemConfigService;

	@InjectMocks
	private ExchangeService exchangeService;

	private static final String TERM = "202510";
	private static final Long MEMBER_ID = 100L;

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

			given(intentRepository.findByTermAndMemberIdOrderByIdDesc(TERM, MEMBER_ID))
					.willReturn(Collections.emptyList());
			given(intentRepository.save(any(ExchangeIntentEntity.class)))
					.willReturn(savedEntity);
			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(Collections.emptyList());
			given(cycleDetector.detectCycles(eq(TERM), any(ExchangeIntentEntity.class)))
					.willReturn(Collections.emptyList());

			IntentCreateResponse response = exchangeService.createIntent(MEMBER_ID, request);

			assertThat(response.getIntentId()).isEqualTo(1L);
			assertThat(response.getGiveCourseNo()).isEqualTo("10001");
			assertThat(response.getWantCourseNo()).isEqualTo("10002");
			assertThat(response.isDeleted()).isFalse();

			verify(redisService).addIntentToFeed(eq(TERM), eq(1L), eq("10001"), eq("10002"), anyString());
			verify(redisService).addGraphEdge(TERM, MEMBER_ID, "10001", "10002");
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

			ExchangeIntentEntity existingIntent = createIntent(1L, MEMBER_ID, "10001", "10002");

			given(intentRepository.findByTermAndMemberIdOrderByIdDesc(TERM, MEMBER_ID))
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

			ExchangeIntentEntity deletedIntent = createIntent(1L, MEMBER_ID, "10001", "10002");
			deletedIntent.delete();

			ExchangeIntentEntity savedEntity = createIntent(2L, MEMBER_ID, "10001", "10002");

			given(intentRepository.findByTermAndMemberIdOrderByIdDesc(TERM, MEMBER_ID))
					.willReturn(List.of(deletedIntent));
			given(intentRepository.save(any(ExchangeIntentEntity.class)))
					.willReturn(savedEntity);
			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(Collections.emptyList());
			given(cycleDetector.detectCycles(eq(TERM), any(ExchangeIntentEntity.class)))
					.willReturn(Collections.emptyList());

			IntentCreateResponse response = exchangeService.createIntent(MEMBER_ID, request);

			assertThat(response.getIntentId()).isEqualTo(2L);
		}

		@Test
		@DisplayName("사이클이 발견되면 방을 생성한다")
		void it_creates_room_when_cycle_detected() {
			given(systemConfigService.getCurrentTerm()).willReturn(TERM);
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			ExchangeIntentEntity newIntent = createIntent(2L, MEMBER_ID, "10001", "10002");
			ExchangeIntentEntity existingIntent = createIntent(1L, 200L, "10002", "10001");

			given(intentRepository.findByTermAndMemberIdOrderByIdDesc(TERM, MEMBER_ID))
					.willReturn(Collections.emptyList());
			given(intentRepository.save(any(ExchangeIntentEntity.class)))
					.willReturn(newIntent);
			given(intentRepository.findByTermAndIsDeletedFalse(TERM))
					.willReturn(List.of(newIntent, existingIntent));
			given(cycleDetector.detectCycles(eq(TERM), any(ExchangeIntentEntity.class)))
					.willReturn(List.of(List.of(existingIntent)));
			given(cycleDetector.computeCycleHash(any()))
					.willReturn("test-hash");
			given(roomRepository.findByTermAndCycleHash(TERM, "test-hash"))
					.willReturn(Optional.empty());
			given(roomRepository.save(any(ExchangeRoomEntity.class)))
					.willAnswer(invocation -> {
						ExchangeRoomEntity entity = invocation.getArgument(0);
						var idField = ExchangeRoomEntity.class.getDeclaredField("id");
						idField.setAccessible(true);
						idField.set(entity, 1L);
						return entity;
					});

			IntentCreateResponse response = exchangeService.createIntent(MEMBER_ID, request);

			assertThat(response.getIntentId()).isEqualTo(2L);
			verify(roomRepository).save(any(ExchangeRoomEntity.class));
			verify(roomMemberRepository).save(any(ExchangeRoomMemberEntity.class));
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

			IntentDeleteResponse response = exchangeService.deleteIntent(MEMBER_ID, intentId);

			assertThat(response.getIntentId()).isEqualTo(intentId);
			assertThat(response.isDeleted()).isTrue();
			assertThat(intent.isDeleted()).isTrue();

			verify(redisService).removeIntentFromFeed(TERM, intentId);
			verify(redisService).removeGraphEdge(TERM, MEMBER_ID, "10001", "10002");
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
			ExchangeIntentEntity intent = createIntent(1L, MEMBER_ID, "10001", "10002");

			given(intentRepository.findByTermAndMemberIdOrderByIdDesc(TERM, MEMBER_ID))
					.willReturn(List.of(intent));
			given(redisService.getMemberRooms(TERM, MEMBER_ID))
					.willReturn(Collections.emptySet());

			var response = exchangeService.getMain(MEMBER_ID);

			assertThat(response.getMyIntents()).hasSize(1);
			assertThat(response.getMyIntents().get(0).getIntentId()).isEqualTo(1L);
			assertThat(response.getMyRooms()).isEmpty();
		}
	}
}
