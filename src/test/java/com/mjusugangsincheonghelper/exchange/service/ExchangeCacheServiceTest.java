package com.mjusugangsincheonghelper.exchange.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import java.time.Duration;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeCacheService 단위 테스트")
class ExchangeCacheServiceTest {

	@Mock
	private RedisTemplate<String, Object> redisTemplate;

	@Mock
	private ValueOperations<String, Object> valueOperations;

	@Mock
	private ExchangeIntentRepository intentRepository;

	@Mock
	private CacheProperties cacheProperties;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private ExchangeCacheService cacheService;

	@BeforeEach
	void setUp() {
		org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	@Nested
	@DisplayName("getFeed 메서드는 (Spring Cache @Cacheable)")
	class Describe_getFeed {

		@Test
		@DisplayName("캐시 미스 시 DB에서 최대 50개 피드를 조회해 반환한다")
		void it_rebuilds_feed_from_db() {
			// Given
			String term = "202620";
			ExchangeIntentEntity intent = ExchangeIntentEntity.builder()
					.term(term).memberId(1L).giveCourseNo("10001").wantCourseNo("10002")
					.build();
			ReflectionTestUtils.setField(intent, "id", 1L);

			given(intentRepository.findByTermAndIsDeletedFalseOrderByIdDesc(eq(term), any(Pageable.class)))
					.willReturn(List.of(intent));

			// When
			List<FeedCacheDto> result = cacheService.getFeed(term);

			// Then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getIntentId()).isEqualTo(1L);
		}

		@Test
		@DisplayName("DB가 비어 있으면 빈 리스트를 반환한다")
		void it_returns_empty_list_when_db_is_empty() {
			// Given
			String term = "202620";
			given(intentRepository.findByTermAndIsDeletedFalseOrderByIdDesc(eq(term), any(Pageable.class)))
					.willReturn(List.of());

			// When
			List<FeedCacheDto> result = cacheService.getFeed(term);

			// Then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("MAIN 읽기 모델 (exchange-main, 단일 writer 경로)")
	class Describe_mainReadModel {

		@Test
		@DisplayName("저장된 MainResponse가 있으면 getStoredMain이 반환한다")
		void it_returns_main_response_from_store() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			String key = "exchange-main::" + term + ":member:" + memberId + ":main:cache";
			MainResponse expected = MainResponse.builder().myIntents(List.of()).recentIntents(List.of()).build();

			given(valueOperations.get(key)).willReturn(expected);

			// When
			MainResponse result = cacheService.getStoredMain(term, memberId);

			// Then
			assertThat(result).isEqualTo(expected);
		}

		@Test
		@DisplayName("storeMain은 항상 올바른 값으로 덮쓰기(SET) 한다")
		void it_sets_main_store() {
			// Given
			String term = "202620";
			Long memberId = 1L;
			String key = "exchange-main::" + term + ":member:" + memberId + ":main:cache";
			MainResponse response = MainResponse.builder().myIntents(List.of()).recentIntents(List.of()).build();
			given(cacheProperties.getTtl("exchange-main")).willReturn(Duration.ofMinutes(10));

			// When
			cacheService.storeMain(term, memberId, response);

			// Then
			verify(valueOperations).set(key, response, Duration.ofMinutes(10));
		}
	}
}
