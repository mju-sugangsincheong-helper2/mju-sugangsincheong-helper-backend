package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadStatusEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.IntentCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomCacheDto;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeCacheService {

	private static final String CACHE_NAME_FEED = "exchange-feed";
	private static final String CACHE_NAME_INTENTS = "exchange-intents";
	private static final String CACHE_NAME_ROOMS = "exchange-rooms";
	private static final long FEED_MAX_SIZE = 50;
	private static final Duration DOUBLE_EVICT_DELAY = Duration.ofSeconds(2);

	private final RedisTemplate<String, Object> redisTemplate;
	private final ExchangeIntentRepository intentRepository;
	private final ExchangeRoomIntentRepository roomIntentRepository;
	private final ExchangeRoomMessageRepository messageRepository;
	private final ExchangeRoomReadStatusRepository readStatusRepository;
	private final ExchangeRoomRepository roomRepository;
	private final TaskScheduler taskScheduler;
	private final CacheProperties cacheProperties;

	public List<FeedCacheDto> getFeed(String term) {
		String key = feedKey(term);
		List<Object> cached = redisTemplate.opsForList().range(key, 0, -1);
		if (cached != null && !cached.isEmpty()) {
			return cached.stream()
					.map(this::toFeedCacheDto)
					.filter(Objects::nonNull)
					.toList();
		}
		return rebuildFeed(term);
	}

	public List<FeedCacheDto> getFeedSlice(String term, Long lastIntentId, int limit) {
		List<FeedCacheDto> feed = getFeed(term);
		if (lastIntentId == null || lastIntentId == 0) {
			return feed.stream().limit(limit).toList();
		}
		boolean found = false;
		List<FeedCacheDto> slice = new ArrayList<>();
		for (FeedCacheDto dto : feed) {
			if (found) {
				slice.add(dto);
				if (slice.size() >= limit) break;
			}
			if (dto.getIntentId().equals(lastIntentId)) {
				found = true;
			}
		}
		return slice;
	}

	public void pushFeed(String term, FeedCacheDto dto) {
		String key = feedKey(term);
		redisTemplate.opsForList().leftPush(key, dto);
		redisTemplate.opsForList().trim(key, 0, FEED_MAX_SIZE - 1);
		redisTemplate.expire(key, cacheProperties.getTtl(CACHE_NAME_FEED));
	}

	public void evictFeed(String term) {
		String key = feedKey(term);
		redisTemplate.delete(key);
		scheduleDoubleEvict(key);
	}

	public List<IntentCacheDto> getIntents(String term, Long memberId) {
		String key = intentsKey(term, memberId);
		List<Object> cached = redisTemplate.opsForList().range(key, 0, -1);
		if (cached != null && !cached.isEmpty()) {
			return cached.stream()
					.map(this::toIntentCacheDto)
					.filter(Objects::nonNull)
					.toList();
		}
		return rebuildIntents(term, memberId);
	}

	public void evictIntents(String term, Long memberId) {
		String key = intentsKey(term, memberId);
		redisTemplate.delete(key);
		scheduleDoubleEvict(key);
	}

	public List<RoomCacheDto> getRooms(String term, Long memberId) {
		String key = roomsKey(term, memberId);
		List<Object> cached = redisTemplate.opsForList().range(key, 0, -1);
		if (cached != null && !cached.isEmpty()) {
			return cached.stream()
					.map(this::toRoomCacheDto)
					.filter(Objects::nonNull)
					.toList();
		}
		return rebuildRooms(term, memberId);
	}

	public void evictRooms(String term, Long memberId) {
		String key = roomsKey(term, memberId);
		redisTemplate.delete(key);
		scheduleDoubleEvict(key);
	}

	private List<FeedCacheDto> rebuildFeed(String term) {
		List<ExchangeIntentEntity> entities = intentRepository.findByTermAndIsDeletedFalseOrderByIdDesc(
				term, PageRequest.of(0, (int) FEED_MAX_SIZE));
		List<FeedCacheDto> dtos = entities.stream().map(FeedCacheDto::from).toList();
		if (!dtos.isEmpty()) {
			String key = feedKey(term);
			redisTemplate.delete(key);
			List<Object> objects = new ArrayList<>(dtos);
			redisTemplate.opsForList().rightPushAll(key, objects);
			redisTemplate.expire(key, cacheProperties.getTtl(CACHE_NAME_FEED));
		}
		return dtos;
	}

	private List<IntentCacheDto> rebuildIntents(String term, Long memberId) {
		List<ExchangeIntentEntity> entities = intentRepository.findByTermAndMemberIdAndIsDeletedFalseOrderByIdDesc(term, memberId);
		List<IntentCacheDto> dtos = entities.stream().map(IntentCacheDto::from).toList();
		if (!dtos.isEmpty()) {
			String key = intentsKey(term, memberId);
			redisTemplate.delete(key);
			List<Object> objects = new ArrayList<>(dtos);
			redisTemplate.opsForList().rightPushAll(key, objects);
			redisTemplate.expire(key, cacheProperties.getTtl(CACHE_NAME_INTENTS));
		}
		return dtos;
	}

	private List<RoomCacheDto> rebuildRooms(String term, Long memberId) {
		List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndMemberIdAndIsOnTrueAndIsDeletedFalse(term, memberId);
		if (roomIntents.isEmpty()) {
			return Collections.emptyList();
		}

		List<RoomCacheDto> dtos = roomIntents.stream()
				.map(ri -> {
					ExchangeRoomReadStatusEntity read = readStatusRepository.findById(
							new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, ri.getRoomId(), memberId)
					).orElse(null);
					Long lastReadId = read != null ? read.getLastReadMessageId() : 0L;

					ExchangeRoomMessageEntity lastMsg = messageRepository.findTopByTermAndRoomIdOrderByIdDesc(term, ri.getRoomId()).orElse(null);
					int unread = messageRepository.countByTermAndRoomIdAndIdGreaterThan(term, ri.getRoomId(), lastReadId);

					ExchangeRoomEntity room = roomRepository.findById(
							new ExchangeRoomEntity.ExchangeRoomId(term, ri.getRoomId())
					).orElse(null);
					boolean isActive = room != null && room.isActive();
					boolean isOn = ri.isOn();

					return RoomCacheDto.builder()
							.roomId(ri.getRoomId())
							.isActive(isActive)
							.isOn(isOn)
							.unreadCount(unread)
							.lastMessageContent(lastMsg != null ? lastMsg.getContent() : null)
							.lastMessageAt(lastMsg != null ? lastMsg.getCreatedAt() : null)
							.build();
				})
				.toList();

		if (!dtos.isEmpty()) {
			String key = roomsKey(term, memberId);
			redisTemplate.delete(key);
			List<Object> objects = new ArrayList<>(dtos);
			redisTemplate.opsForList().rightPushAll(key, objects);
			redisTemplate.expire(key, cacheProperties.getTtl(CACHE_NAME_ROOMS));
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
		return null;
	}

	private IntentCacheDto toIntentCacheDto(Object obj) {
		if (obj instanceof IntentCacheDto dto) return dto;
		return null;
	}

	private RoomCacheDto toRoomCacheDto(Object obj) {
		if (obj instanceof RoomCacheDto dto) return dto;
		return null;
	}

	private String feedKey(String term) {
		return "exchange::" + term + ":feed:cache";
	}

	private String intentsKey(String term, Long memberId) {
		return "exchange::" + term + ":member:" + memberId + ":intents:cache";
	}

	private String roomsKey(String term, Long memberId) {
		return "exchange::" + term + ":member:" + memberId + ":rooms:cache";
	}
}
