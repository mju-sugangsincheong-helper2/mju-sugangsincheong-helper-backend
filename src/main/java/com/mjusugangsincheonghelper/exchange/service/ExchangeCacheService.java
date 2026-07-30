package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeCacheService {

	private static final String CACHE_NAME_FEED = "exchange-feed";
	private static final String CACHE_NAME_MAIN = "exchange-main";
	private static final long FEED_MAX_SIZE = 50;
	private static final Duration DOUBLE_EVICT_DELAY = Duration.ofSeconds(2);

	private final RedisTemplate<String, Object> redisTemplate;
	private final ExchangeIntentRepository intentRepository;
	private final TaskScheduler taskScheduler;
	private final CacheProperties cacheProperties;
	private final ObjectMapper objectMapper;

	public List<FeedCacheDto> getFeed(String term) {
		String key = feedKey(term);
		try {
			List<Object> cached = redisTemplate.opsForList().range(key, 0, -1);
			if (cached != null && !cached.isEmpty()) {
				List<FeedCacheDto> dtos = cached.stream()
						.map(this::toFeedCacheDto)
						.filter(Objects::nonNull)
						.toList();
				if (!dtos.isEmpty()) {
					return dtos;
				}
			}
		} catch (Exception e) {
			log.warn("Redis getFeed failed for key={}: {}", key, e.getMessage());
		}
		return rebuildFeed(term);
	}

	public void pushFeed(String term, FeedCacheDto dto) {
		String key = feedKey(term);
		try {
			redisTemplate.opsForList().leftPush(key, dto);
			redisTemplate.opsForList().trim(key, 0, FEED_MAX_SIZE - 1);
			redisTemplate.expire(key, cacheProperties.getTtl(CACHE_NAME_FEED));
		} catch (Exception e) {
			log.warn("Redis pushFeed failed for key={}: {}", key, e.getMessage());
		}
	}

	public void evictFeed(String term) {
		String key = feedKey(term);
		try {
			redisTemplate.delete(key);
		} catch (Exception e) {
			log.warn("Redis evictFeed failed for key={}: {}", key, e.getMessage());
		}
		scheduleDoubleEvict(key);
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

	private List<FeedCacheDto> rebuildFeed(String term) {
		List<ExchangeIntentEntity> entities = intentRepository.findByTermAndIsDeletedFalseOrderByIdDesc(
				term, PageRequest.of(0, (int) FEED_MAX_SIZE));
		List<FeedCacheDto> dtos = entities.stream().map(FeedCacheDto::from).toList();
		if (!dtos.isEmpty()) {
			try {
				String key = feedKey(term);
				redisTemplate.delete(key);
				List<Object> objects = new ArrayList<>(dtos);
				redisTemplate.opsForList().rightPushAll(key, objects);
				redisTemplate.expire(key, cacheProperties.getTtl(CACHE_NAME_FEED));
			} catch (Exception e) {
				log.warn("Redis rebuildFeed cache save failed: {}", e.getMessage());
			}
		}
		return dtos;
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

	private FeedCacheDto toFeedCacheDto(Object obj) {
		if (obj instanceof FeedCacheDto dto) return dto;
		if (obj != null) {
			try {
				return objectMapper.convertValue(obj, FeedCacheDto.class);
			} catch (Exception e) {
				log.warn("Failed to convert feed cache object: {}", e.getMessage());
			}
		}
		return null;
	}

	private String feedKey(String term) {
		return "exchange::" + term + ":feed:cache";
	}

	private String mainKey(String term, Long memberId) {
		return "exchange::" + term + ":member:" + memberId + ":main:cache";
	}
}
