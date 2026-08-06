package com.mjusugangsincheonghelper.exchange.service;

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
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.IntentItem;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.MessageSummaryItem;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.ParticipantItem;
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
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomMetaCacheDto;
import com.mjusugangsincheonghelper.exchange.event.ExchangeEvents;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeService {

	private final ExchangeIntentRepository intentRepository;
	private final ExchangeRoomIntentRepository roomIntentRepository;
	private final ExchangeRoomMessageRepository messageRepository;
	private final ExchangeRoomReadStatusRepository readStatusRepository;
	private final ExchangeRoomRepository roomRepository;
	private final ExchangeCacheService cacheService;
	private final SystemConfigService systemConfigService;
	private final ApplicationEventPublisher eventPublisher;

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

		ExchangeIntentEntity saved;
		try {
			saved = intentRepository.saveAndFlush(ExchangeIntentEntity.builder()
					.term(term)
					.memberId(memberId)
					.giveCourseNo(request.getGiveCourseNo())
					.wantCourseNo(request.getWantCourseNo())
					.build());
		} catch (DataIntegrityViolationException e) {
			throw new BaseException(ErrorCode.EXCHANGE_DUPLICATE_INTENT);
		}

		eventPublisher.publishEvent(new ExchangeEvents.IntentCreated(
				term, saved.getId(), memberId, saved.getGiveCourseNo(), saved.getWantCourseNo()));

		log.info("Created exchange intent. term={}, memberId={}, intentId={}, give={}, want={}",
				term, memberId, saved.getId(), saved.getGiveCourseNo(), saved.getWantCourseNo());

		return IntentCreateResponse.from(saved);
	}

	@Transactional
	public IntentDeleteResponse deleteIntent(Long memberId, Long intentId) {
		String term = systemConfigService.getCurrentTerm();

		ExchangeIntentEntity intent = intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentId))
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_FOUND));

		if (!intent.getMemberId().equals(memberId)) {
			throw new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_OWNER);
		}

		if (intent.isDeleted()) {
			throw new BaseException(ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED);
		}

		intent.markDeleted();

		List<ExchangeRoomIntentEntity> affectedRoomIntents = roomIntentRepository.findByTermAndIntentId(term, intentId);
		List<Long> affectedRoomIds = affectedRoomIntents.stream()
				.map(ExchangeRoomIntentEntity::getRoomId)
				.distinct()
				.toList();
		List<Long> affectedMemberIds = affectedRoomIntents.stream()
				.map(ExchangeRoomIntentEntity::getMemberId)
				.distinct()
				.toList();

		for (ExchangeRoomIntentEntity ri : affectedRoomIntents) {
			ri.markDeleted();
			updateRoomStatusAndState(term, ri.getRoomId(), intentId, memberId);
		}

		eventPublisher.publishEvent(new ExchangeEvents.IntentDeleted(term, memberId, affectedRoomIds, affectedMemberIds));

		log.info("Deleted exchange intent. term={}, memberId={}, intentId={}", term, memberId, intentId);

		return IntentDeleteResponse.builder()
				.intentId(intentId)
				.isDeleted(true)
				.deletedAt(intent.getDeletedAt())
				.build();
	}

	public MainResponse getMain(Long memberId) {
		String term = systemConfigService.getCurrentTerm();

		MainResponse cached = cacheService.getMainCache(term, memberId);
		if (cached != null) {
			return cached;
		}

		List<IntentCacheDto> myIntents = cacheService.getMemberIntents(term, memberId);

		List<IntentItem> intentItems = new ArrayList<>();
		for (IntentCacheDto intent : myIntents) {
			List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndIntentId(term, intent.getIntentId());
			List<RoomItem> roomItems = new ArrayList<>();

			for (ExchangeRoomIntentEntity roomIntent : roomIntents) {
				RoomMetaCacheDto roomMeta = cacheService.getRoomMeta(term, roomIntent.getRoomId());
				if (roomMeta == null) {
					continue;
				}

				ExchangeRoomReadStatusEntity readStatus = readStatusRepository.findById(
						new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, roomIntent.getRoomId(), memberId)).orElse(null);
				Long lastReadMessageId = readStatus != null ? readStatus.getLastReadMessageId() : 0L;

				boolean isOn = roomIntent.isOn();
				int unread = isOn ? messageRepository.countByTermAndRoomIdAndIdGreaterThan(term, roomIntent.getRoomId(), lastReadMessageId) : 0;

				MessageSummaryItem summaryItem = null;
				if (isOn && roomMeta.getLastMessage() != null) {
					RoomMetaCacheDto.MessageSummary lastMessage = roomMeta.getLastMessage();
					summaryItem = MessageSummaryItem.builder()
							.messageId(lastMessage.getMessageId())
							.senderId(lastMessage.getSenderId())
							.messageType(lastMessage.getMessageType())
							.content(lastMessage.getContent())
							.createdAt(lastMessage.getCreatedAt())
							.build();
				}

				List<ParticipantItem> participantItems = roomMeta.getParticipants().stream()
						.map(participant -> ParticipantItem.builder()
								.memberId(participant.getMemberId())
								.intentId(participant.getIntentId())
								.giveCourseNo(participant.getGiveCourseNo())
								.wantCourseNo(participant.getWantCourseNo())
								.isDeleted(participant.isDeleted())
								.isOn(participant.isOn())
								.build())
						.toList();

				roomItems.add(RoomItem.builder()
						.roomId(roomIntent.getRoomId())
						.term(term)
						.cycleHash(roomMeta.getCycleHash())
						.status(roomMeta.getStatus())
						.isOn(isOn)
						.unreadCount(unread)
						.lastReadMessageId(lastReadMessageId)
						.lastMessage(summaryItem)
						.participants(participantItems)
						.createdAt(roomMeta.getCreatedAt())
						.build());
			}

			intentItems.add(IntentItem.builder()
					.intentId(intent.getIntentId())
					.giveCourseNo(intent.getGiveCourseNo())
					.wantCourseNo(intent.getWantCourseNo())
					.isDeleted(intent.isDeleted())
					.createdAt(intent.getCreatedAt())
					.rooms(roomItems)
					.build());
		}

		List<FeedCacheDto> feed = cacheService.getFeed(term);
		List<RecentIntentItem> recentItems = feed.stream()
				.map(feedItem -> RecentIntentItem.builder()
						.intentId(feedItem.getIntentId())
						.giveCourseNo(feedItem.getGiveCourseNo())
						.wantCourseNo(feedItem.getWantCourseNo())
						.createdAt(feedItem.getCreatedAt())
						.build())
				.toList();

		MainResponse response = MainResponse.builder()
				.myIntents(intentItems)
				.recentIntents(recentItems)
				.build();

		cacheService.putMainCache(term, memberId, response);
		return response;
	}

	public RecentIntentsResponse getRecentIntents() {
		String term = systemConfigService.getCurrentTerm();
		List<FeedCacheDto> feed = cacheService.getFeed(term);

		List<IntentFeedItem> items = feed.stream()
				.map(dto -> IntentFeedItem.builder()
						.intentId(dto.getIntentId())
						.giveCourseNo(dto.getGiveCourseNo())
						.wantCourseNo(dto.getWantCourseNo())
						.createdAt(dto.getCreatedAt())
						.build())
				.toList();

		return RecentIntentsResponse.builder()
				.recentIntents(items)
				.build();
	}

	@Transactional
	public MessageResponse getMessages(Long memberId, Long roomId, Long beforeMessageId, int size) {
		String term = systemConfigService.getCurrentTerm();

		List<ExchangeRoomIntentEntity> myRoomIntents = roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId);
		if (myRoomIntents.isEmpty()) {
			throw new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER);
		}

		PageRequest pageable = PageRequest.of(0, size + 1);
		List<ExchangeRoomMessageEntity> messages;
		if (beforeMessageId == null || beforeMessageId >= Long.MAX_VALUE || beforeMessageId <= 0) {
			messages = messageRepository.findByTermAndRoomIdOrderByIdDesc(term, roomId, pageable);
		} else {
			messages = messageRepository.findByTermAndRoomIdAndIdLessThanOrderByIdDesc(term, roomId, beforeMessageId, pageable);
		}

		boolean hasNext = messages.size() > size;
		List<ExchangeRoomMessageEntity> result = hasNext ? messages.subList(0, size) : messages;

		List<MessageItem> items = result.stream()
				.map(m -> MessageItem.builder()
						.messageId(m.getId())
						.senderId(m.getMemberId())
						.messageType(m.getMessageType())
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

		Long nextBeforeMessageId = result.isEmpty() ? beforeMessageId : result.get(result.size() - 1).getId();

		eventPublisher.publishEvent(new ExchangeEvents.RoomViewed(term, roomId, memberId));

		return MessageResponse.builder()
				.roomId(roomId)
				.messages(items)
				.nextBeforeMessageId(nextBeforeMessageId)
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

		List<ExchangeRoomIntentEntity> allRoomIntents = roomIntentRepository.findByTermAndRoomId(term, roomId);
		long activeCount = allRoomIntents.stream().filter(ri -> !ri.isDeleted()).count();
		if (activeCount < 2) {
			throw new BaseException(ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED);
		}

		ExchangeRoomMessageEntity saved = messageRepository.save(ExchangeRoomMessageEntity.builder()
				.term(term)
				.roomId(roomId)
				.memberId(memberId)
				.intentId(activeRi.getIntentId())
				.messageType("TALK")
				.content(request.getContent())
				.build());

		ExchangeRoomReadStatusEntity read = readStatusRepository.findById(
				new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, roomId, memberId))
				.orElse(null);
		if (read != null) {
			read.updateLastReadMessageId(saved.getId());
		}

		eventPublisher.publishEvent(new ExchangeEvents.RoomMessageSent(term, roomId, memberId, request.getContent()));

		return MessageSendResponse.from(saved);
	}

	@Transactional
	public RoomToggleResponse toggleRoom(Long memberId, Long roomId, RoomToggleRequest request) {
		String term = systemConfigService.getCurrentTerm();

		List<ExchangeRoomIntentEntity> myRoomIntents = roomIntentRepository.findByTermAndRoomIdAndMemberId(term, roomId, memberId);
		if (myRoomIntents.isEmpty()) {
			throw new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER);
		}

		ExchangeRoomIntentEntity activeRi = myRoomIntents.stream()
				.filter(ri -> !ri.isDeleted())
				.findFirst()
				.orElse(null);

		for (ExchangeRoomIntentEntity ri : myRoomIntents) {
			ri.toggle(request.isOn());
		}

		updateRoomStatusAndState(term, roomId, activeRi != null ? activeRi.getIntentId() : null, memberId);

		eventPublisher.publishEvent(new ExchangeEvents.RoomToggled(term, roomId));

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
		int activeCount = n - d;

		ExchangeRoomEntity room = roomRepository.findByIdForUpdate(term, roomId)
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_FOUND));

		String newStatus;
		if (d == n) {
			newStatus = "ALL_DELETE";
		} else if (d > 0) {
			newStatus = "PARTIAL_DELETE";
		} else if (o > 0) {
			newStatus = "PARTIAL_OFF";
		} else {
			newStatus = "ACTIVE";
		}

		String oldStatus = room.getStatus();
		room.updateStatus(newStatus);
		roomRepository.save(room);

		if (!newStatus.equals(oldStatus)) {
			log.debug("Exchange room status changed. term={}, roomId={}, from={}, to={}", term, roomId, oldStatus, newStatus);
		}

		String systemContent = null;
		if (newStatus.equals("ALL_DELETE") && !oldStatus.equals("ALL_DELETE")) {
			systemContent = "[시스템] 모든 참여자의 교환 의사 철회로 인해 대화방이 비활성화되었습니다.";
		} else if (newStatus.equals("PARTIAL_DELETE")) {
			if (activeCount < 2) {
				int prevActiveCount = n - (d - 1);
				if (!oldStatus.equals("PARTIAL_DELETE") || prevActiveCount >= 2) {
					systemContent = "[시스템] 일부 참여자의 교환 의사 철회로 인해 대화방이 비활성화되었습니다.";
				}
			} else {
				if (!oldStatus.equals("PARTIAL_DELETE")) {
					systemContent = "[시스템] 일부 참여자가 교환 의사를 철회하였습니다.";
				}
			}
		} else if (newStatus.equals("PARTIAL_OFF") && !oldStatus.equals("PARTIAL_OFF")) {
			systemContent = "[시스템] 일부 참여자가 대화방 알림을 OFF 하였습니다.";
		} else if (newStatus.equals("ACTIVE") && "PARTIAL_OFF".equals(oldStatus)) {
			systemContent = "[시스템] 모든 참여자가 대화방 알림을 ON으로 전환하였습니다.";
		}

		if (systemContent != null) {
			messageRepository.save(ExchangeRoomMessageEntity.builder()
					.term(term)
					.roomId(roomId)
					.memberId(null)
					.intentId(null)
					.messageType("SYSTEM")
					.content(systemContent)
					.build());
		}
	}
}
