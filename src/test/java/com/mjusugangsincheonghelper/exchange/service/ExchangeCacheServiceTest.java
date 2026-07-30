package com.mjusugangsincheonghelper.exchange.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeCacheService 단위 테스트")
@SuppressWarnings("unchecked")
class ExchangeCacheServiceTest {

	@Mock
	private RedisTemplate<String, Object> redisTemplate;

	@Mock
	private ListOperations<String, Object> listOperations;

	@Mock
	private ValueOperations<String, Object> valueOperations;

	@Mock
	private ExchangeIntentRepository intentRepository;

	@Mock
	private TaskScheduler taskScheduler;

	@Mock
	private CacheProperties cacheProperties;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private ExchangeCacheService cacheService;

	@BeforeEach
	void setUp() {
		org.mockito.Mockito.lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
		org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	@Nested
	@DisplayName("getFeed 메서드는")
	class Describe_getFeed {

		@Test
		@DisplayName("캐시가 존재하면 캐시된 피드 리스트를 반환한다")
		void it_returns_cached_feed() {
			// Given
			String term = "202620";
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
			String term = "202620";
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
	@DisplayName("pushFeed 메서드는")
	class Describe_pushFeed {

		@Test
		@DisplayName("피드를 캐시에 추가하고 크기를 제한하며 TTL을 설정한다")
		void it_pushes_feed_and_trims() {
			// Given
			String term = "202620";
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
			String term = "202620";
			String key = "exchange::" + term + ":feed:cache";

			doAnswer(invocation -> {
				Runnable runnable = invocation.getArgument(0);
				runnable.run();
				return null;
			}).when(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));

			// When
			cacheService.evictFeed(term);

			// Then
			verify(redisTemplate, times(2)).delete(key);
		}
	}

	@Nested
	@DisplayName("getMainCache / putMainCache / evictMainCache 메서드는")
	class Describe_mainCache {

		@Test
		@DisplayName("캐시된 MainResponse가 있으면 반환한다")
		void it_returns_main_response_from_cache() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			String key = "exchange::" + term + ":member:" + memberId + ":main:cache";
			MainResponse expected = MainResponse.builder().myIntents(List.of()).recentIntents(List.of()).build();

			given(valueOperations.get(key)).willReturn(expected);

			// When
			MainResponse result = cacheService.getMainCache(term, memberId);

			// Then
			assertThat(result).isEqualTo(expected);
		}

		@Test
		@DisplayName("putMainCache로 MainResponse를 캐시에 저장한다")
		void it_puts_main_cache() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			String key = "exchange::" + term + ":member:" + memberId + ":main:cache";
			MainResponse response = MainResponse.builder().myIntents(List.of()).recentIntents(List.of()).build();
			given(cacheProperties.getTtl("exchange-main")).willReturn(Duration.ofMinutes(10));

			// When
			cacheService.putMainCache(term, memberId, response);

			// Then
			verify(valueOperations).set(key, response, Duration.ofMinutes(10));
		}

		@Test
		@DisplayName("evictMainCache는 캐시를 삭제하고 이중 무효화를 스케줄링한다")
		void it_evicts_main_cache() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			String key = "exchange::" + term + ":member:" + memberId + ":main:cache";

			doAnswer(invocation -> {
				Runnable runnable = invocation.getArgument(0);
				runnable.run();
				return null;
			}).when(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));

			// When
			cacheService.evictMainCache(term, memberId);

			// Then
			verify(redisTemplate, times(2)).delete(key);
		}
	}
}
