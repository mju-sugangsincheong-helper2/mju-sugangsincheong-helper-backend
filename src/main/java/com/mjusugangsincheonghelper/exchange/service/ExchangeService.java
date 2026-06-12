package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMemberRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadRepository;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.CycleDetail;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.IntentItem;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.RoomSummary;
import com.mjusugangsincheonghelper.exchange.dto.MessageResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageResponse.MessageItem;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendRequest;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse.IntentFeedItem;
import com.mjusugangsincheonghelper.exchange.dto.cache.IntentDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RecentIntentDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomActiveIntentsDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomDynamicMetaDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomStaticMetaDto;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeService {

	private final ExchangeIntentRepository intentRepository;
	private final ExchangeRoomMemberRepository roomMemberRepository;
	private final ExchangeMessageRepository messageRepository;
	private final ExchangeRoomReadRepository roomReadRepository;
	private final ExchangeCycleDetector cycleDetector;
	private final ExchangeUserCacheService userCacheService;
	private final ExchangeRoomCacheService roomCacheService;
	private final ExchangePageCacheService pageCacheService;
	private final CacheManager cacheManager;
	private final SystemConfigService systemConfigService;

	@Transactional
	public IntentCreateResponse createIntent(Long memberId, IntentCreateRequest request) {
		String term = systemConfigService.getCurrentTerm();

		if (request.getGiveCourseNo().equals(request.getWantCourseNo())) {
			throw new BaseException(ErrorCode.EXCHANGE_SAME_COURSE);
		}

		List<IntentDto> myIntents = userCacheService.getUserIntents(term, memberId);
		boolean duplicate = myIntents.stream()
				.filter(i -> !i.isDeleted())
				.anyMatch(i -> i.getGiveCourseNo().equals(request.getGiveCourseNo())
						&& i.getWantCourseNo().equals(request.getWantCourseNo()));
		if (duplicate) {
			throw new BaseException(ErrorCode.EXCHANGE_DUPLICATE_INTENT);
		}

		ExchangeIntentEntity saved = intentRepository.save(ExchangeIntentEntity.builder()
				.term(term)
				.memberId(memberId)
				.giveCourseNo(request.getGiveCourseNo())
				.wantCourseNo(request.getWantCourseNo())
				.build());

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				evictCache("user-intents", term + ":member:" + memberId + ":intents:cache");
				evictAllCache("recent-intents-page");
				cycleDetector.detectCyclesAndCreateRooms(term, saved);
			}
		});

		return IntentCreateResponse.from(saved);
	}

	@Transactional
	public IntentDeleteResponse deleteIntent(Long memberId, Long intentId) {
		String term = systemConfigService.getCurrentTerm();

		ExchangeIntentEntity intent = intentRepository.findById(
				new ExchangeIntentEntity.ExchangeIntentId(term, intentId))
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_FOUND));

		if (!intent.getMemberId().equals(memberId)) {
			throw new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_OWNER);
		}

		if (intent.isDeleted()) {
			throw new BaseException(ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED);
		}

		intent.delete();

		List<Long> memberRoomIds = roomMemberRepository.findByTermAndMemberId(term, memberId).stream()
				.map(ExchangeRoomMemberEntity::getRoomId)
				.distinct()
				.toList();

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				evictCache("user-intents", term + ":member:" + memberId + ":intents:cache");
				evictAllCache("recent-intents-page");
				for (Long roomId : memberRoomIds) {
					evictCache("room-active-intents", term + ":room:" + roomId + ":active_intents:cache");
				}
			}
		});

		return IntentDeleteResponse.builder()
				.message("교환 의사가 철회되었습니다.")
				.timestamp(Instant.now())
				.intentId(intentId)
				.isDeleted(true)
				.build();
	}

	public MainResponse getMain(Long memberId) {
		String term = systemConfigService.getCurrentTerm();

		List<IntentDto> myIntents = userCacheService.getUserIntents(term, memberId);
		List<Long> roomIds = userCacheService.getUserRoomIds(term, memberId);
		Map<Long, Integer> unreadCounts = userCacheService.getUserUnreadCounts(term, memberId);

		List<IntentItem> intentItems = myIntents.stream()
				.map(i -> IntentItem.builder()
						.intentId(i.getIntentId())
						.giveCourseNo(i.getGiveCourseNo())
						.wantCourseNo(i.getWantCourseNo())
						.isDeleted(i.isDeleted())
						.createdAt(i.getCreatedAt())
						.build())
				.collect(Collectors.toList());

		List<RoomSummary> myRooms = roomIds.stream().map(roomId -> {
			RoomStaticMetaDto staticMeta = roomCacheService.getRoomStaticMeta(term, roomId);
			RoomDynamicMetaDto dynamicMeta = roomCacheService.getRoomDynamicMeta(term, roomId);
			RoomActiveIntentsDto activeIntents = roomCacheService.getRoomActiveIntents(term, roomId);

			int activeCount = activeIntents.calculateActiveCount();
			int unread = unreadCounts.getOrDefault(roomId, 0);

			List<CycleDetail> cycleDetails = staticMeta.getCycleDetails().stream()
					.map(d -> CycleDetail.builder()
							.memberId(d.getMemberId())
							.giveCourseNo(d.getGiveCourseNo())
							.wantCourseNo(d.getWantCourseNo())
							.build())
					.toList();

			return RoomSummary.builder()
					.roomId(roomId)
					.totalParticipants(staticMeta.getTotalParticipants())
					.activeIntentCount(activeCount)
					.unreadMessageCount(unread)
					.lastMessage(dynamicMeta.getLastMessage())
					.lastMessageAt(dynamicMeta.getLastMessageAt())
					.cycleDetails(cycleDetails)
					.build();
		}).collect(Collectors.toList());

		return MainResponse.builder()
				.message("메인 상태 조회 성공")
				.timestamp(Instant.now())
				.myIntents(intentItems)
				.myRooms(myRooms)
				.build();
	}

	public RecentIntentsResponse getRecentIntents(Long lastIntentId, int limit) {
		String term = systemConfigService.getCurrentTerm();

		List<RecentIntentDto> cached = pageCacheService.getRecentIntentsPage(term, lastIntentId, limit + 1);

		boolean hasNext = cached.size() > limit;
		List<RecentIntentDto> result = hasNext ? cached.subList(0, limit) : cached;

		List<IntentFeedItem> items = result.stream()
				.map(dto -> IntentFeedItem.builder()
						.intentId(dto.getIntentId())
						.giveCourseNo(dto.getGiveCourseNo())
						.wantCourseNo(dto.getWantCourseNo())
						.createdAt(dto.getCreatedAt())
						.build())
				.collect(Collectors.toList());

		Long nextLastIntentId = result.isEmpty() ? lastIntentId : result.get(result.size() - 1).getIntentId();

		return RecentIntentsResponse.builder()
				.message("최근 등록된 교환 의사 조회 성공")
				.timestamp(Instant.now())
				.intents(items)
				.nextLastIntentId(nextLastIntentId)
				.hasNext(hasNext)
				.build();
	}

	@Transactional
	public MessageResponse getMessages(Long memberId, Long roomId, Long lastMessageId, int size) {
		String term = systemConfigService.getCurrentTerm();

		roomMemberRepository.findById(new ExchangeRoomMemberEntity.ExchangeRoomMemberId(term, roomId, memberId))
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER));

		PageRequest pageable = PageRequest.of(0, size + 1);
		List<ExchangeMessageEntity> messages = messageRepository.findByTermAndRoomIdAndIdLessThanOrderByIdDesc(term, roomId, lastMessageId, pageable);

		boolean hasNext = messages.size() > size;
		List<ExchangeMessageEntity> result = hasNext ? messages.subList(0, size) : messages;

		List<MessageItem> items = result.stream()
				.map(MessageItem::from)
				.collect(Collectors.toList());

		if (!result.isEmpty()) {
			Long minId = result.stream().mapToLong(ExchangeMessageEntity::getId).min().orElse(0L);
			ExchangeRoomReadEntity read = roomReadRepository.findById(
					new ExchangeRoomReadEntity.ExchangeRoomReadId(term, roomId, memberId))
					.orElse(ExchangeRoomReadEntity.builder().term(term).roomId(roomId).memberId(memberId).build());
			read.updateLastReadMessageId(minId);
			roomReadRepository.save(read);
		}

		List<Long> memberIds = roomMemberRepository.findMemberIdsByRoomId(term, roomId);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (Long mid : memberIds) {
					evictCache("user-unread-counts", term + ":member:" + mid + ":unread_counts:cache");
				}
			}
		});

		Long nextLastMessageId = result.isEmpty() ? lastMessageId : result.get(result.size() - 1).getId();

		return MessageResponse.builder()
				.message("메시지 조회 성공")
				.timestamp(Instant.now())
				.roomId(roomId)
				.messages(items)
				.nextLastMessageId(nextLastMessageId)
				.hasNext(hasNext)
				.build();
	}

	@Transactional
	public MessageSendResponse sendMessage(Long memberId, Long roomId, MessageSendRequest request) {
		String term = systemConfigService.getCurrentTerm();

		roomMemberRepository.findById(new ExchangeRoomMemberEntity.ExchangeRoomMemberId(term, roomId, memberId))
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER));

		ExchangeMessageEntity saved = messageRepository.save(ExchangeMessageEntity.builder()
				.term(term)
				.roomId(roomId)
				.senderId(memberId)
				.content(request.getContent())
				.build());

		List<Long> memberIds = roomMemberRepository.findMemberIdsByRoomId(term, roomId);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				evictCache("room-dynamic-meta", term + ":room:" + roomId + ":dynamic_meta:cache");
				for (Long mid : memberIds) {
					evictCache("user-unread-counts", term + ":member:" + mid + ":unread_counts:cache");
				}
			}
		});

		return MessageSendResponse.from(saved);
	}

	private void evictCache(String cacheName, String key) {
		Cache cache = cacheManager.getCache(cacheName);
		if (cache != null) {
			cache.evict(key);
		}
	}

	private void evictAllCache(String cacheName) {
		Cache cache = cacheManager.getCache(cacheName);
		if (cache != null) {
			cache.clear();
		}
	}
}
