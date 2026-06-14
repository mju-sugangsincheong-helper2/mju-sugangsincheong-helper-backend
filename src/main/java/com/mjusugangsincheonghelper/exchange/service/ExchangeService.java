package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadStatusEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.exchange.dto.CycleDetectionMessage;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.IntentItem;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.RecentIntentItem;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.RoomItem;
import com.mjusugangsincheonghelper.exchange.dto.MessageResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageResponse.MessageItem;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendRequest;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse.IntentFeedItem;
import com.mjusugangsincheonghelper.exchange.dto.RoomToggleRequest;
import com.mjusugangsincheonghelper.exchange.dto.RoomToggleResponse;
import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.IntentCacheDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomCacheDto;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
	private final ExchangeRoomIntentRepository roomIntentRepository;
	private final ExchangeRoomMessageRepository messageRepository;
	private final ExchangeRoomReadStatusRepository readStatusRepository;
	private final ExchangeRoomRepository roomRepository;
	private final ExchangeCycleDetector cycleDetector;
	private final ExchangeCacheService cacheService;
	private final SystemConfigService systemConfigService;

	@Transactional
	public IntentCreateResponse createIntent(Long memberId, IntentCreateRequest request) {
		String term = systemConfigService.getCurrentTerm();

		if (request.getGiveCourseNo().equals(request.getWantCourseNo())) {
			throw new BaseException(ErrorCode.EXCHANGE_SAME_COURSE);
		}

		List<ExchangeIntentEntity> duplicates = intentRepository.findByTermAndMemberIdAndGiveCourseNoAndWantCourseNoAndIsDeletedFalse(
				term, memberId, request.getGiveCourseNo(), request.getWantCourseNo());
		if (!duplicates.isEmpty()) {
			throw new BaseException(ErrorCode.EXCHANGE_DUPLICATE_INTENT);
		}

		ExchangeIntentEntity saved = intentRepository.save(ExchangeIntentEntity.builder()
				.term(term)
				.memberId(memberId)
				.giveCourseNo(request.getGiveCourseNo())
				.wantCourseNo(request.getWantCourseNo())
				.build());

		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					cacheService.pushFeed(term, FeedCacheDto.from(saved));
					cacheService.evictIntents(term, memberId);
					cycleDetector.enqueueCycleDetection(CycleDetectionMessage.builder()
							.term(term)
							.intentId(saved.getId())
							.memberId(memberId)
							.giveCourseNo(saved.getGiveCourseNo())
							.wantCourseNo(saved.getWantCourseNo())
							.build());
				}
			});
		} else {
			cacheService.pushFeed(term, FeedCacheDto.from(saved));
			cacheService.evictIntents(term, memberId);
			cycleDetector.enqueueCycleDetection(CycleDetectionMessage.builder()
					.term(term)
					.intentId(saved.getId())
					.memberId(memberId)
					.giveCourseNo(saved.getGiveCourseNo())
					.wantCourseNo(saved.getWantCourseNo())
					.build());
		}

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

		intent.markDeleted();

		List<ExchangeRoomIntentEntity> affectedRoomIntents = roomIntentRepository.findByTermAndIntentId(term, intentId);
		List<Long> affectedMemberIds = affectedRoomIntents.stream()
				.map(ExchangeRoomIntentEntity::getMemberId)
				.distinct()
				.toList();

		for (ExchangeRoomIntentEntity ri : affectedRoomIntents) {
			ri.markDeleted();
			updateRoomStatusAndState(term, ri.getRoomId(), intentId, memberId);
		}

		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					cacheService.evictIntents(term, memberId);
					for (Long mid : affectedMemberIds) {
						cacheService.evictRooms(term, mid);
					}
				}
			});
		} else {
			cacheService.evictIntents(term, memberId);
			for (Long mid : affectedMemberIds) {
				cacheService.evictRooms(term, mid);
			}
		}

		return IntentDeleteResponse.builder()
				.intentId(intentId)
				.isDeleted(true)
				.deletedAt(intent.getDeletedAt())
				.build();
	}

	public MainResponse getMain(Long memberId) {
		String term = systemConfigService.getCurrentTerm();

		List<IntentCacheDto> myIntents = cacheService.getIntents(term, memberId);
		List<RoomCacheDto> myRooms = cacheService.getRooms(term, memberId);
		List<FeedCacheDto> feed = cacheService.getFeedSlice(term, null, 5);

		List<IntentItem> intentItems = myIntents.stream()
				.map(i -> IntentItem.builder()
						.intentId(i.getIntentId())
						.giveCourseNo(i.getGiveCourseNo())
						.wantCourseNo(i.getWantCourseNo())
						.isDeleted(i.isDeleted())
						.createdAt(i.getCreatedAt())
						.build())
				.toList();

		List<RoomItem> roomItems = myRooms.stream()
				.map(r -> RoomItem.builder()
						.roomId(r.getRoomId())
						.isActive(r.isActive())
						.isOn(r.isOn())
						.unreadCount(r.getUnreadCount())
						.lastMessageContent(r.getLastMessageContent())
						.lastMessageAt(r.getLastMessageAt())
						.build())
				.toList();

		List<RecentIntentItem> recentItems = feed.stream()
				.map(f -> RecentIntentItem.builder()
						.intentId(f.getIntentId())
						.giveCourseNo(f.getGiveCourseNo())
						.wantCourseNo(f.getWantCourseNo())
						.createdAt(f.getCreatedAt())
						.build())
				.toList();

		return MainResponse.builder()
				.myIntents(intentItems)
				.myRooms(roomItems)
				.recentIntents(recentItems)
				.build();
	}

	public RecentIntentsResponse getRecentIntents(Long lastIntentId, int limit) {
		String term = systemConfigService.getCurrentTerm();

		List<FeedCacheDto> feed = cacheService.getFeedSlice(term, lastIntentId, limit + 1);

		boolean hasNext = feed.size() > limit;
		List<FeedCacheDto> result = hasNext ? feed.subList(0, limit) : feed;

		List<IntentFeedItem> items = result.stream()
				.map(dto -> IntentFeedItem.builder()
						.intentId(dto.getIntentId())
						.giveCourseNo(dto.getGiveCourseNo())
						.wantCourseNo(dto.getWantCourseNo())
						.createdAt(dto.getCreatedAt())
						.build())
				.toList();

		Long nextLastIntentId = result.isEmpty() ? lastIntentId : result.get(result.size() - 1).getIntentId();

		return RecentIntentsResponse.builder()
				.intents(items)
				.nextLastIntentId(nextLastIntentId)
				.hasNext(hasNext)
				.build();
	}

	@Transactional
	public MessageResponse getMessages(Long memberId, Long roomId, Long lastMessageId, int size) {
		String term = systemConfigService.getCurrentTerm();

		List<ExchangeRoomIntentEntity> myRoomIntents = roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId);
		if (myRoomIntents.isEmpty()) {
			throw new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER);
		}

		PageRequest pageable = PageRequest.of(0, size + 1);
		List<ExchangeRoomMessageEntity> messages;
		if (lastMessageId == null || lastMessageId >= Long.MAX_VALUE) {
			messages = messageRepository.findByTermAndRoomIdOrderByIdDesc(term, roomId, pageable);
		} else {
			messages = messageRepository.findByTermAndRoomIdAndIdLessThanOrderByIdDesc(term, roomId, lastMessageId, pageable);
		}

		boolean hasNext = messages.size() > size;
		List<ExchangeRoomMessageEntity> result = hasNext ? messages.subList(0, size) : messages;

		List<MessageItem> items = result.stream()
				.map(m -> MessageItem.builder()
						.messageId(m.getId())
						.senderId(m.getMemberId())
						.content(m.getContent())
						.createdAt(m.getCreatedAt())
						.build())
				.toList();

		if (!result.isEmpty()) {
			Long maxId = result.stream().mapToLong(ExchangeRoomMessageEntity::getId).max().orElse(0L);
			ExchangeRoomIntentEntity activeRi = myRoomIntents.stream()
					.filter(ri -> !ri.isDeleted())
					.findFirst()
					.orElse(myRoomIntents.get(0));

			ExchangeRoomReadStatusEntity read = readStatusRepository.findById(
					new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, roomId, memberId))
					.orElse(ExchangeRoomReadStatusEntity.builder()
							.term(term)
							.roomId(roomId)
							.memberId(memberId)
							.intentId(activeRi.getIntentId())
							.build());
			read.updateLastReadMessageId(maxId);
			readStatusRepository.save(read);
		}

		Long nextLastMessageId = result.isEmpty() ? lastMessageId : result.get(result.size() - 1).getId();

		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					cacheService.evictRooms(term, memberId);
				}
			});
		} else {
			cacheService.evictRooms(term, memberId);
		}

		return MessageResponse.builder()
				.roomId(roomId)
				.messages(items)
				.nextLastMessageId(nextLastMessageId)
				.hasNext(hasNext)
				.build();
	}

	@Transactional
	public MessageSendResponse sendMessage(Long memberId, Long roomId, MessageSendRequest request) {
		String term = systemConfigService.getCurrentTerm();

		List<ExchangeRoomIntentEntity> myRoomIntents = roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId);
		if (myRoomIntents.isEmpty()) {
			throw new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER);
		}

		ExchangeRoomIntentEntity activeRi = myRoomIntents.stream()
				.filter(ri -> !ri.isDeleted())
				.findFirst()
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED));

		ExchangeRoomMessageEntity saved = messageRepository.save(ExchangeRoomMessageEntity.builder()
				.term(term)
				.roomId(roomId)
				.memberId(memberId)
				.intentId(activeRi.getIntentId())
				.content(request.getContent())
				.build());

		ExchangeRoomReadStatusEntity read = readStatusRepository.findById(
				new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, roomId, memberId))
				.orElse(null);
		if (read != null) {
			read.updateLastReadMessageId(saved.getId());
		}

		List<Long> memberIds = roomIntentRepository.findDistinctMemberIdsByTermAndRoomId(term, roomId);

		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					for (Long mid : memberIds) {
						cacheService.evictRooms(term, mid);
					}
				}
			});
		} else {
			for (Long mid : memberIds) {
				cacheService.evictRooms(term, mid);
			}
		}

		return MessageSendResponse.from(saved);
	}

	@Transactional
	public RoomToggleResponse toggleRoom(Long memberId, Long roomId, RoomToggleRequest request) {
		String term = systemConfigService.getCurrentTerm();

		List<ExchangeRoomIntentEntity> myRoomIntents = roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId);
		if (myRoomIntents.isEmpty()) {
			throw new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER);
		}

		for (ExchangeRoomIntentEntity ri : myRoomIntents) {
			ri.toggle(request.isOn());
		}

		updateRoomStatusAndState(term, roomId, null, null);

		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					cacheService.evictRooms(term, memberId);
				}
			});
		} else {
			cacheService.evictRooms(term, memberId);
		}

		return RoomToggleResponse.builder()
				.roomId(roomId)
				.isOn(request.isOn())
				.build();
	}

	private void updateRoomStatusAndState(String term, Long roomId, Long triggerIntentId, Long triggerMemberId) {
		List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, roomId);
		int n = roomIntents.size();
		int d = (int) roomIntents.stream().filter(ExchangeRoomIntentEntity::isDeleted).count();
		int o = (int) roomIntents.stream().filter(ri -> !ri.isDeleted() && !ri.isOn()).count();

		ExchangeRoomEntity room = roomRepository.findByIdForUpdate(term, roomId)
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_FOUND));

		String newStatus;
		boolean newIsActive;

		if (n - d < 2) {
			newStatus = "ALL_DELETE";
			newIsActive = false;
		} else if (d > 0) {
			newStatus = "PARTIAL_DELETE";
			newIsActive = true;
		} else if (o > 0) {
			newStatus = "PARTIAL_OFF";
			newIsActive = true;
		} else {
			newStatus = "ACTIVE";
			newIsActive = true;
		}

		String oldStatus = room.getStatus();
		room.updateStatus(newStatus, newIsActive);
		roomRepository.save(room);

		if (triggerIntentId != null && triggerMemberId != null && !newStatus.equals(oldStatus)) {
			if (newStatus.equals("ALL_DELETE")) {
				messageRepository.save(ExchangeRoomMessageEntity.builder()
						.term(term)
						.roomId(roomId)
						.memberId(triggerMemberId)
						.intentId(triggerIntentId)
						.content("[시스템] 참여자의 교환 의사 철회로 인해 대화방이 비활성화되었습니다.")
						.build());
			} else if (newStatus.equals("PARTIAL_DELETE")) {
				messageRepository.save(ExchangeRoomMessageEntity.builder()
						.term(term)
						.roomId(roomId)
						.memberId(triggerMemberId)
						.intentId(triggerIntentId)
						.content("[시스템] 일부 참여자가 교환 의사를 철회하였습니다.")
						.build());
			}
		}
	}
}
