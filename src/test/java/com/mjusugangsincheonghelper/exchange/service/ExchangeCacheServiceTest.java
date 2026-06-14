package com.mjusugangsincheonghelper.exchange.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadStatusEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.IntentCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomCacheDto;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeCacheService 단위 테스트")
class ExchangeCacheServiceTest {

	@Mock
	private RedisTemplate<String, Object> redisTemplate;

	@Mock
	private ListOperations<String, Object> listOperations;

	@Mock
	private ExchangeIntentRepository intentRepository;

	@Mock
	private ExchangeRoomIntentRepository roomIntentRepository;

	@Mock
	private ExchangeRoomMessageRepository messageRepository;

	@Mock
	private ExchangeRoomReadStatusRepository readStatusRepository;

	@Mock
	private ExchangeRoomRepository roomRepository;

	@Mock
	private TaskScheduler taskScheduler;

	@Mock
	private CacheProperties cacheProperties;

	@InjectMocks
	private ExchangeCacheService cacheService;

	@BeforeEach
	void setUp() {
		org.mockito.Mockito.lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
	}

	@Nested
	@DisplayName("getFeed 메서드는")
	class Describe_getFeed {

		@Test
		@DisplayName("캐시가 존재하면 캐시된 피드 리스트를 반환한다")
		void it_returns_cached_feed() {
			// Given
			String term = "202510";
			String key = "exchange::" + term + ":feed:cache";
			FeedCacheDto dto = FeedCacheDto.builder().intentId(1L).build();

			given(listOperations.range(key, 0, -1)).willReturn(List.of(dto));

			// When
			List<FeedCacheDto> result = cacheService.getFeed(term);

			// Then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getIntentId()).isEqualTo(1L);
			verify(intentRepository, never()).findByTermAndIsDeletedFalseOrderByIdDesc(any(), any());
		}

		@Test
		@DisplayName("캐시가 비어있으면 DB에서 조회하여 피드를 재빌드하고 반환한다")
		void it_rebuilds_feed_from_db_on_cache_miss() {
			// Given
			String term = "202510";
			String key = "exchange::" + term + ":feed:cache";

			given(listOperations.range(key, 0, -1)).willReturn(Collections.emptyList());

			ExchangeIntentEntity intent = ExchangeIntentEntity.builder()
					.term(term).memberId(1L).giveCourseNo("10001").wantCourseNo("10002")
					.build();
			ReflectionTestUtils.setField(intent, "id", 1L);

			given(intentRepository.findByTermAndIsDeletedFalseOrderByIdDesc(eq(term), any(Pageable.class)))
					.willReturn(List.of(intent));
			given(cacheProperties.getTtl("exchange-feed")).willReturn(Duration.ofMinutes(10));

			// When
			List<FeedCacheDto> result = cacheService.getFeed(term);

			// Then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getIntentId()).isEqualTo(1L);
			verify(redisTemplate).delete(key);
			verify(listOperations).rightPushAll(eq(key), any(List.class));
		}
	}

	@Nested
	@DisplayName("getFeedSlice 메서드는")
	class Describe_getFeedSlice {

		@Test
		@DisplayName("인덱스 조건에 맞게 최근 피드 슬라이스를 페이징 처리하여 반환한다")
		void it_returns_feed_slice() {
			// Given
			String term = "202510";
			String key = "exchange::" + term + ":feed:cache";

			FeedCacheDto dto1 = FeedCacheDto.builder().intentId(10L).build();
			FeedCacheDto dto2 = FeedCacheDto.builder().intentId(20L).build();
			FeedCacheDto dto3 = FeedCacheDto.builder().intentId(30L).build();

			given(listOperations.range(key, 0, -1)).willReturn(List.of(dto1, dto2, dto3));

			// When
			List<FeedCacheDto> resultNoCursor = cacheService.getFeedSlice(term, null, 2);
			List<FeedCacheDto> resultWithCursor = cacheService.getFeedSlice(term, 20L, 2);

			// Then
			assertThat(resultNoCursor).hasSize(2);
			assertThat(resultNoCursor.get(0).getIntentId()).isEqualTo(10L);

			assertThat(resultWithCursor).hasSize(1);
			assertThat(resultWithCursor.get(0).getIntentId()).isEqualTo(30L);
		}
	}

	@Nested
	@DisplayName("pushFeed 메서드는")
	class Describe_pushFeed {

		@Test
		@DisplayName("피드를 캐시에 추가하고 크기를 제한하며 TTL을 설정한다")
		void it_pushes_feed_and_trims() {
			// Given
			String term = "202510";
			String key = "exchange::" + term + ":feed:cache";
			FeedCacheDto dto = FeedCacheDto.builder().intentId(1L).build();

			given(cacheProperties.getTtl("exchange-feed")).willReturn(Duration.ofMinutes(10));

			// When
			cacheService.pushFeed(term, dto);

			// Then
			verify(listOperations).leftPush(key, dto);
			verify(listOperations).trim(key, 0, 49L);
			verify(redisTemplate).expire(key, Duration.ofMinutes(10));
		}
	}

	@Nested
	@DisplayName("evictFeed 메서드는")
	class Describe_evictFeed {

		@Test
		@DisplayName("캐시를 즉시 삭제하고 2초 후 2차 이중 무효화를 스케줄링한다")
		void it_deletes_cache_and_schedules_double_evict() {
			// Given
			String term = "202510";
			String key = "exchange::" + term + ":feed:cache";

			doAnswer(invocation -> {
				Runnable runnable = invocation.getArgument(0);
				runnable.run(); // 즉시 실행하여 검증
				return null;
			}).when(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));

			// When
			cacheService.evictFeed(term);

			// Then
			verify(redisTemplate, times(2)).delete(key);
		}
	}

	@Nested
	@DisplayName("getIntents 메서드는")
	class Describe_getIntents {

		@Test
		@DisplayName("유저의 의사 캐시가 존재하면 캐시를 반환한다")
		void it_returns_cached_intents() {
			// Given
			String term = "202510";
			Long memberId = 1L;
			String key = "exchange::" + term + ":member:" + memberId + ":intents:cache";
			IntentCacheDto dto = IntentCacheDto.builder().intentId(5L).build();

			given(listOperations.range(key, 0, -1)).willReturn(List.of(dto));

			// When
			List<IntentCacheDto> result = cacheService.getIntents(term, memberId);

			// Then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getIntentId()).isEqualTo(5L);
			verify(intentRepository, never()).findByTermAndMemberIdAndIsDeletedFalseOrderByIdDesc(any(), any());
		}
	}

	@Nested
	@DisplayName("getRooms 메서드는")
	class Describe_getRooms {

		@Test
		@DisplayName("유저의 방 캐시가 존재하면 캐시를 반환하고, 없으면 DB에서 조인 조회하여 재빌드한다")
		void it_returns_cached_rooms_or_rebuilds_from_db() {
			// Given
			String term = "202510";
			Long memberId = 1L;
			String key = "exchange::" + term + ":member:" + memberId + ":rooms:cache";

			given(listOperations.range(key, 0, -1)).willReturn(Collections.emptyList());

			ExchangeRoomIntentEntity ri = ExchangeRoomIntentEntity.builder()
					.term(term).roomId(100L).intentId(10L).memberId(memberId)
					.build();
			ReflectionTestUtils.setField(ri, "isOn", true);

			given(roomIntentRepository.findByTermAndMemberIdAndIsOnTrueAndIsDeletedFalse(term, memberId))
					.willReturn(List.of(ri));

			ExchangeRoomReadStatusEntity rs = ExchangeRoomReadStatusEntity.builder()
					.term(term).roomId(100L).memberId(memberId).intentId(10L)
					.build();
			ReflectionTestUtils.setField(rs, "lastReadMessageId", 1000L);

			given(readStatusRepository.findById(any())).willReturn(Optional.of(rs));

			ExchangeRoomMessageEntity lastMsg = ExchangeRoomMessageEntity.builder()
					.term(term).roomId(100L).memberId(memberId).intentId(10L).content("Hello")
					.build();
			given(messageRepository.findTopByTermAndRoomIdOrderByIdDesc(term, 100L)).willReturn(Optional.of(lastMsg));
			given(messageRepository.countByTermAndRoomIdAndIdGreaterThan(term, 100L, 1000L)).willReturn(2);

			ExchangeRoomEntity room = ExchangeRoomEntity.builder().term(term).cycleHash("hash").status("ACTIVE").isActive(true).build();
			given(roomRepository.findById(any())).willReturn(Optional.of(room));

			given(cacheProperties.getTtl("exchange-rooms")).willReturn(Duration.ofMinutes(10));

			// When
			List<RoomCacheDto> result = cacheService.getRooms(term, memberId);

			// Then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getRoomId()).isEqualTo(100L);
			assertThat(result.get(0).getUnreadCount()).isEqualTo(2);
			assertThat(result.get(0).getLastMessageContent()).isEqualTo("Hello");
		}
	}
}
