package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 교환 도메인 캐싱.
 *
 * <p>피드(feed)는 읽기 전용 최신 목록이므로 Spring Cache({@code @Cacheable}/{@code @CacheEvict})로
 * 관리한다(캐시 미스 시 DB에서 재구성). 회원별 메인 응답은 별도 키(memberId)의 값 캐시로 관리한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeCacheService {

	private static final String CACHE_NAME_FEED = "exchange-feed";
	private static final String CACHE_NAME_MAIN = "exchange-main";
	private static final int FEED_MAX_SIZE = 50;
	private static final Duration DOUBLE_EVICT_DELAY = Duration.ofSeconds(2);

	private final ExchangeIntentRepository intentRepository;
	private final RedisTemplate<String, Object> redisTemplate;
	private final CacheProperties cacheProperties;
	private final TaskScheduler taskScheduler;
	private final ObjectMapper objectMapper;

	/**
	 * 최신 교환 신청 피드(최대 50개). 캐시 히트 시 캐시된 목록, 미스 시 DB에서 재구성해 캐시한다.
	 */
	@Cacheable(cacheNames = CACHE_NAME_FEED, key = "#term")
	public List<FeedCacheDto> getFeed(String term) {
		return intentRepository.findByTermAndIsDeletedFalseOrderByIdDesc(term, PageRequest.of(0, FEED_MAX_SIZE))
				.stream()
				.map(FeedCacheDto::from)
				.toList();
	}

	/**
	 * 피드가 바뀌는 쓰기(신청/삭제) 직후 호출 — 다음 {@link #getFeed(String)}에서 DB로 재구성된다.
	 */
	@CacheEvict(cacheNames = CACHE_NAME_FEED, key = "#term")
	public void evictFeed(String term) {
		// 무효화만 수행 (본문 필요 없음)
	}

	public MainResponse getMainCache(String term, Long memberId) {
		String key = mainKey(term, memberId);
		try {
			Object value = redisTemplate.opsForValue().get(key);
			if (value instanceof MainResponse response) {
				return response;
			}
			if (value != null) {
				return objectMapper.convertValue(value, MainResponse.class);
			}
		} catch (Exception e) {
			log.warn("Redis getMainCache failed for key={}: {}", key, e.getMessage());
		}
		return null;
	}

	public void putMainCache(String term, Long memberId, MainResponse response) {
		if (response == null) return;
		String key = mainKey(term, memberId);
		try {
			redisTemplate.opsForValue().set(key, response, cacheProperties.getTtl(CACHE_NAME_MAIN));
		} catch (Exception e) {
			log.warn("Redis putMainCache failed for key={}: {}", key, e.getMessage());
		}
	}

	public void evictMainCache(String term, Long memberId) {
		String key = mainKey(term, memberId);
		try {
			redisTemplate.delete(key);
		} catch (Exception e) {
			log.warn("Redis evictMainCache failed for key={}: {}", key, e.getMessage());
		}
		scheduleDoubleEvict(key);
	}

	private void scheduleDoubleEvict(String key) {
		taskScheduler.schedule(() -> {
			try {
				redisTemplate.delete(key);
			} catch (Exception e) {
				log.warn("Double evict failed for key={}: {}", key, e.getMessage());
			}
		}, java.time.Instant.now().plus(DOUBLE_EVICT_DELAY));
	}

	private String mainKey(String term, Long memberId) {
		return "exchange::" + term + ":member:" + memberId + ":main:cache";
	}
}