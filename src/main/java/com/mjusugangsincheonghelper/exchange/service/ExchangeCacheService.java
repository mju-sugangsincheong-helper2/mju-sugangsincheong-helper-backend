package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.IntentCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomMetaCacheDto;
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
 * 교환(Exchange) 도메인 캐싱 파사드.
 *
 * <p>읽기 경로는 read-through 캐시({@code @Cacheable})로 RDB 조회 결과를 캐시하고, RDB를 단일 진실 공급원으로 유지한다.
 * 쓰기 트랜잭션 커밋 후 {@code TransactionSynchronization.afterCommit()}에서 관련 캐시를 evict 한다
 * (singlegame/multigame 랭킹과 달리 exchange는 TTL에 기대지 않는 명시적 evict 패턴을 사용한다).</p>
 *
 * <p>캐시 이름/키 규칙은 docs/redis_key_naming_rule.md 를 따른다. 캐시 이름은 도메인명(exchange-) 접두사를 사용한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeCacheService {

	private static final String CACHE_NAME_FEED = CacheProperties.EXCHANGE_FEED;
	private static final String CACHE_NAME_MAIN = CacheProperties.EXCHANGE_MAIN;
	private static final String CACHE_NAME_USER_INTENTS = CacheProperties.EXCHANGE_USER_INTENTS;
	private static final String CACHE_NAME_ROOM_META = CacheProperties.EXCHANGE_ROOM_META;

	private static final int FEED_MAX_SIZE = 50;

	private final ExchangeIntentRepository intentRepository;
	private final ExchangeRoomIntentRepository roomIntentRepository;
	private final ExchangeRoomMessageRepository messageRepository;
	private final ExchangeRoomRepository roomRepository;
	private final RedisTemplate<String, Object> redisTemplate;
	private final CacheProperties cacheProperties;
	private final TaskScheduler taskScheduler;
	private final ObjectMapper objectMapper;

	// ============ 읽기 (read-through cache) ============

	/**
	 * 최근 등록된 교환 신청 피드(최대 50개). 캐시 히트 시 캐시된 목록, 미스 시 DB에서 재구성해 캐시한다.
	 */
	@Cacheable(cacheNames = CACHE_NAME_FEED, key = "#term")
	public List<FeedCacheDto> getFeed(String term) {
		return intentRepository.findByTermAndIsDeletedFalseOrderByIdDesc(term, PageRequest.of(0, FEED_MAX_SIZE))
				.stream()
				.map(FeedCacheDto::from)
				.toList();
	}

	/**
	 * 회원의 비삭제 교환 의도 목록 (exchange-user-intents).
	 * evict: 의도 등록/철회 시 {@link #evictMemberIntents(String, Long)}
	 */
	@Cacheable(cacheNames = CACHE_NAME_USER_INTENTS, key = "#term + ':member:' + #memberId + ':intents:cache'")
	public List<IntentCacheDto> getMemberIntents(String term, Long memberId) {
		return intentRepository.findByTermAndMemberIdAndIsDeletedFalseOrderByIdDesc(term, memberId)
				.stream()
				.map(IntentCacheDto::from)
				.toList();
	}

	/**
	 * 방 단위 메타데이터 — 정적 정보(cycleHash, createdAt) + 동적 정보(상태, 마지막 메시지, 참여자 목록)
	 * (exchange-room-meta). evict: 메시지 전송/방 토글/의도 철회 시 {@link #evictRoomMeta(String, Long)}
	 */
	@Cacheable(cacheNames = CACHE_NAME_ROOM_META, key = "#term + ':room:' + #roomId + ':meta:cache'")
	public RoomMetaCacheDto getRoomMeta(String term, Long roomId) {
		ExchangeRoomEntity room = roomRepository.findById(new ExchangeRoomEntity.ExchangeRoomId(term, roomId)).orElse(null);
		if (room == null) {
			return null;
		}
		ExchangeRoomMessageEntity lastMessage = messageRepository.findTopByTermAndRoomIdOrderByIdDesc(term, roomId).orElse(null);
		List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, roomId);
		return RoomMetaCacheDto.from(room, lastMessage, roomIntents,
				intentId -> intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentId)).orElse(null));
	}

	// ============ 쓰기 (evict) ============

	/**
	 * 피드가 바뀌는 쓰기(신청/삭제) 직후 호출 — 다음 {@link #getFeed(String)}에서 DB로 재구성된다.
	 */
	@CacheEvict(cacheNames = CACHE_NAME_FEED, key = "#term")
	public void evictFeed(String term) {
	}

	/**
	 * 회원 의도 목록이 바뀌는 쓰기(등록/철회) 직후 호출.
	 */
	@CacheEvict(cacheNames = CACHE_NAME_USER_INTENTS, key = "#term + ':member:' + #memberId + ':intents:cache'")
	public void evictMemberIntents(String term, Long memberId) {
	}

	/**
	 * 방 메타데이터가 바뀌는 쓰기(메시지 전송/방 토글/의도 철회) 직후 호출.
	 */
	@CacheEvict(cacheNames = CACHE_NAME_ROOM_META, key = "#term + ':room:' + #roomId + ':meta:cache'")
	public void evictRoomMeta(String term, Long roomId) {
	}

	// ============ 메인 응답 캐시 (exchange-main, 회원별 전체 응답 레벨) ============

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
			log.warn("Redis getMainCache failed. key={}, message={}", key, e.getMessage(), e);
		}
		return null;
	}

	public void putMainCache(String term, Long memberId, MainResponse response) {
		if (response == null) return;
		String key = mainKey(term, memberId);
		try {
			redisTemplate.opsForValue().set(key, response, cacheProperties.getTtl(CACHE_NAME_MAIN));
		} catch (Exception e) {
			log.warn("Redis putMainCache failed. key={}, message={}", key, e.getMessage(), e);
		}
	}

	public void evictMainCache(String term, Long memberId) {
		String key = mainKey(term, memberId);
		try {
			redisTemplate.delete(key);
		} catch (Exception e) {
			log.warn("Redis evictMainCache failed. key={}, message={}", key, e.getMessage(), e);
		}
		scheduleDoubleEvict(key);
	}

	private void scheduleDoubleEvict(String key) {
		taskScheduler.schedule(() -> {
			try {
				redisTemplate.delete(key);
			} catch (Exception e) {
				log.warn("Double evict failed. key={}, message={}", key, e.getMessage(), e);
			}
		}, java.time.Instant.now().plus(cacheProperties.getDoubleEvictDelay()));
	}

	private String mainKey(String term, Long memberId) {
		return "exchange-main::" + term + ":member:" + memberId + ":main:cache";
	}
}
