package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.IntentCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomMetaCacheDto;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 교환(Exchange) 도메인 캐싱 파사드.
 *
 * <p>읽기 경로는 read-through 캐시({@code @Cacheable})로 RDB 조회 결과를 캐시하고, RDB를 단일 진실 공급원으로 유지한다.</p>
 *
 * <p>최상위 {@code exchange-main}(회원별 전체 응답)은 <b>단일 writer 경로 + 멱등 덮쓰기</b> 패턴을 쓴다.
 * 갱신은 오직 writer(afterCommit 핸들러)만 담당하며, 항상 올바른 값으로 {@code SET} 한다(삭제가 아님).
 * reader는 조회만 하고 절대 쓰지 않으므로, "evict 이후 동시 reader가 옛 데이터를 다시 캐시하는"
 * read-through 재캐시 vs evict 순서 역전 경합이 구조적으로 발생하지 않는다. (기존의 2초 double-evict는 제거)</p>
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
	private static final String CACHE_NAME_ROOM_META = CacheProperties.EXCHANGE_ROOM_META;

	private static final int FEED_MAX_SIZE = 50;

	private final ExchangeIntentRepository intentRepository;
	private final ExchangeRoomIntentRepository roomIntentRepository;
	private final ExchangeRoomMessageRepository messageRepository;
	private final ExchangeRoomRepository roomRepository;
	private final MemberRepository memberRepository;
	private final RedisTemplate<String, Object> redisTemplate;
	private final CacheProperties cacheProperties;
	private final ObjectMapper objectMapper;

	// ============ 읽기 (read-through cache) ============

	/**
	 * 최근 등록된 교환 신청 피드(최대 50개). 캐시 히트 시 캐시된 목록, 미스 시 DB에서 재구성해 캐시한다.
	 */
	@Cacheable(cacheNames = CACHE_NAME_FEED, key = "#term", condition = "#term != null")
	public List<FeedCacheDto> getFeed(String term) {
		return intentRepository.findByTermAndIsDeletedFalseOrderByIdDesc(term, PageRequest.of(0, FEED_MAX_SIZE))
				.stream()
				.map(FeedCacheDto::from)
				.toList();
	}

	/**
	 * 회원의 비삭제 교환 의도 목록 — MAIN 조립용.
	 * read-through 캐시를 두지 않고 항상 DB에서 최신 값을 읽는다(MAIN은 writer가 직접 조립하므로
	 * 별도 캐시 레이어가 불필요하고, 캐시 경합도 피할 수 있다).
	 */
	public List<IntentCacheDto> computeMemberIntents(String term, Long memberId) {
		return intentRepository.findByTermAndMemberIdAndIsDeletedFalseOrderByIdDesc(term, memberId)
				.stream()
				.map(IntentCacheDto::from)
				.toList();
	}

	/**
	 * 방 단위 메타데이터 — 정적 정보(cycleHash, createdAt) + 동적 정보(상태, 마지막 메시지, 참여자 목록).
	 * read-through 캐시(getRoomMeta)와 조립용 비캐시(computeRoomMeta)가 공유하는 DB 조회 본체.
	 */
	private RoomMetaCacheDto computeRoomMetaBody(String term, Long roomId) {
		ExchangeRoomEntity room = roomRepository.findById(new ExchangeRoomEntity.ExchangeRoomId(term, roomId)).orElse(null);
		if (room == null) {
			return null;
		}
		ExchangeRoomMessageEntity lastMessage = messageRepository.findTopByTermAndRoomIdOrderByIdDesc(term, roomId).orElse(null);
		List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, roomId);
		return RoomMetaCacheDto.from(room, lastMessage, roomIntents,
				intentId -> intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentId)).orElse(null),
				memberId -> memberRepository.findById(memberId).orElse(null));
	}

	@Cacheable(cacheNames = CACHE_NAME_ROOM_META, key = "#term + ':room:' + #roomId + ':meta:cache'", condition = "#term != null")
	public RoomMetaCacheDto getRoomMeta(String term, Long roomId) {
		return computeRoomMetaBody(term, roomId);
	}

	/** MAIN 조립용 — 캐시를 거치지 않고 항상 DB에서 최신 room 메타를 계산한다. */
	public RoomMetaCacheDto computeRoomMeta(String term, Long roomId) {
		return computeRoomMetaBody(term, roomId);
	}

	// ============ 쓰기 (evict) ============

	/**
	 * 피드가 바뀌는 쓰기(신청/삭제) 직후 호출 — 다음 {@link #getFeed(String)}에서 DB로 재구성된다.
	 */
	@CacheEvict(cacheNames = CACHE_NAME_FEED, key = "#term", condition = "#term != null")
	public void evictFeed(String term) {
	}

	/**
	 * 방 메타데이터가 바뀌는 쓰기(메시지 전송/방 토글/의도 철회) 직후 호출.
	 */
	@CacheEvict(cacheNames = CACHE_NAME_ROOM_META, key = "#term + ':room:' + #roomId + ':meta:cache'", condition = "#term != null")
	public void evictRoomMeta(String term, Long roomId) {
	}

	// ============ MAIN 읽기 모델 (exchange-main, 단일 writer 경로) ============
	// reader는 조회만 한다. 갱신은 오직 writer(rebuildMemberMain)만 담당 → evict/재캐시 경합 소멸.

	/**
	 * reader 전용 조회. 저장된 {@link MainResponse}가 있으면 반환, 없으면 null.
	 * 이 메서드는 절대 쓰지 않는다(reader write-back 금지).
	 */
	public MainResponse getStoredMain(String term, Long memberId) {
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
			log.warn("Redis getStoredMain failed. key={}, message={}", key, e.getMessage(), e);
		}
		return null;
	}

	/**
	 * writer 전용 갱신. 항상 올바른 값으로 덮쓰기(SET) 하므로 순서 역전이无意义.
	 * reader는 호출 금지.
	 */
	public void storeMain(String term, Long memberId, MainResponse response) {
		if (response == null) {
			return;
		}
		String key = mainKey(term, memberId);
		try {
			redisTemplate.opsForValue().set(key, response, cacheProperties.getTtl(CACHE_NAME_MAIN));
		} catch (Exception e) {
			log.warn("Redis storeMain failed. key={}, message={}", key, e.getMessage(), e);
		}
	}

	private String mainKey(String term, Long memberId) {
		return "exchange-main::" + term + ":member:" + memberId + ":main:cache";
	}
}
