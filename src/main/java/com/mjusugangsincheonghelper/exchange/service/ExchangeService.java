package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadStatusEntity;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.exchange.dto.CycleDetectionMessage;
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
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notification.consumer.NotificationConsumerWorker;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
	private final MemberDeviceRepository memberDeviceRepository;
	private final PgmqService pgmqService;
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

		executeAfterCommit(() -> {
			cacheService.pushFeed(term, FeedCacheDto.from(saved));
			cacheService.evictMainCache(term, memberId);
			cycleDetector.enqueueCycleDetection(CycleDetectionMessage.builder()
					.term(term)
					.intentId(saved.getId())
					.memberId(memberId)
					.giveCourseNo(saved.getGiveCourseNo())
					.wantCourseNo(saved.getWantCourseNo())
					.build());
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

		executeAfterCommit(() -> {
			cacheService.evictMainCache(term, memberId);
			for (Long mid : affectedMemberIds) {
				if (!mid.equals(memberId)) {
					cacheService.evictMainCache(term, mid);
				}
			}
		});

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

		List<ExchangeIntentEntity> myIntents = intentRepository.findByTermAndMemberIdAndIsDeletedFalseOrderByIdDesc(term, memberId);

		List<IntentItem> intentItems = new ArrayList<>();
		for (ExchangeIntentEntity intent : myIntents) {
			List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndIntentId(term, intent.getId());
			List<RoomItem> roomItems = new ArrayList<>();

			for (ExchangeRoomIntentEntity ri : roomIntents) {
				ExchangeRoomEntity room = roomRepository.findById(
						new ExchangeRoomEntity.ExchangeRoomId(term, ri.getRoomId())
				).orElse(null);
				if (room == null) continue;

				ExchangeRoomReadStatusEntity read = readStatusRepository.findById(
						new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, ri.getRoomId(), memberId)
				).orElse(null);
				Long lastReadId = read != null ? read.getLastReadMessageId() : 0L;

				boolean isOn = ri.isOn();
				ExchangeRoomMessageEntity lastMsg = isOn ? messageRepository.findTopByTermAndRoomIdOrderByIdDesc(term, ri.getRoomId()).orElse(null) : null;
				int unread = isOn ? messageRepository.countByTermAndRoomIdAndIdGreaterThan(term, ri.getRoomId(), lastReadId) : 0;

				MessageSummaryItem summaryItem = (isOn && lastMsg != null) ? MessageSummaryItem.builder()
						.messageId(lastMsg.getId())
						.senderId(lastMsg.getMemberId())
						.messageType(lastMsg.getMessageType())
						.content(lastMsg.getContent())
						.createdAt(lastMsg.getCreatedAt())
						.build() : null;

				List<ExchangeRoomIntentEntity> allRoomIntents = roomIntentRepository.findByTermAndRoomId(term, ri.getRoomId());
				List<ParticipantItem> participantItems = new ArrayList<>();
				for (ExchangeRoomIntentEntity pri : allRoomIntents) {
					ExchangeIntentEntity pi = intentRepository.findById(
							new ExchangeIntentEntity.ExchangeIntentId(term, pri.getIntentId())
					).orElse(null);

					participantItems.add(ParticipantItem.builder()
							.memberId(pri.getMemberId())
							.intentId(pri.getIntentId())
							.giveCourseNo(pi != null ? pi.getGiveCourseNo() : null)
							.wantCourseNo(pi != null ? pi.getWantCourseNo() : null)
							.isDeleted(pri.isDeleted())
							.isOn(pri.isOn())
							.build());
				}

				roomItems.add(RoomItem.builder()
						.roomId(ri.getRoomId())
						.term(term)
						.cycleHash(room.getCycleHash())
						.status(room.getStatus())
						.isOn(ri.isOn())
						.unreadCount(unread)
						.lastReadMessageId(lastReadId)
						.lastMessage(summaryItem)
						.participants(participantItems)
						.createdAt(room.getCreatedAt())
						.build());
			}

			intentItems.add(IntentItem.builder()
					.intentId(intent.getId())
					.giveCourseNo(intent.getGiveCourseNo())
					.wantCourseNo(intent.getWantCourseNo())
					.isDeleted(intent.isDeleted())
					.createdAt(intent.getCreatedAt())
					.rooms(roomItems)
					.build());
		}

		List<FeedCacheDto> feed = cacheService.getFeed(term);
		List<RecentIntentItem> recentItems = feed.stream()
				.map(f -> RecentIntentItem.builder()
						.intentId(f.getIntentId())
						.giveCourseNo(f.getGiveCourseNo())
						.wantCourseNo(f.getWantCourseNo())
						.createdAt(f.getCreatedAt())
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

		executeAfterCommit(() -> {
			cacheService.evictMainCache(term, memberId);
		});

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

		List<Long> memberIds = roomIntentRepository.findDistinctMemberIdsByTermAndRoomId(term, roomId);

		executeAfterCommit(() -> {
			for (Long mid : memberIds) {
				cacheService.evictMainCache(term, mid);
			}
			sendFcmForRoomMessage(term, roomId, memberId, request.getContent());
		});

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

		executeAfterCommit(() -> {
			cacheService.evictMainCache(term, memberId);
		});

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

	private void sendFcmForRoomMessage(String term, Long roomId, Long senderMemberId, String content) {
		try {
			List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, roomId);
			List<Long> targetMemberIds = roomIntents.stream()
					.filter(ri -> ri.isOn() && !ri.isDeleted() && !ri.getMemberId().equals(senderMemberId))
					.map(ExchangeRoomIntentEntity::getMemberId)
					.distinct()
					.toList();

			if (targetMemberIds.isEmpty()) {
				log.debug("No active target members to receive FCM notification for roomId={}", roomId);
				return;
			}

			String path = "/exchange/rooms/" + roomId;
			String timestamp = String.valueOf(System.currentTimeMillis());

			for (Long targetMemberId : targetMemberIds) {
				List<MemberDevice> devices = memberDeviceRepository.findByMemberId(targetMemberId);
				for (MemberDevice device : devices) {
					String token = device.getFcmToken();
					if (token != null && !token.isBlank()) {
						NotificationEventMessage event = NotificationEventMessage.builder()
								.token(token)
								.notification(NotificationEventMessage.NotificationPayload.builder()
										.title("수강신청 교환 대화방 메시지")
										.body(content)
										.build())
								.data(Map.of(
										"type", "EXCHANGE_MESSAGE",
										"path", path,
										"timestamp", timestamp
								))
								.build();
						pgmqService.send(NotificationConsumerWorker.QUEUE_NAME, event);
						log.info("Queued FCM notification for EXCHANGE_MESSAGE: memberId={}, deviceId={}, path={}",
								targetMemberId, device.getId(), path);
					}
				}
			}
		} catch (Exception e) {
			log.warn("FCM 알림 발송 중 오류 발생: roomId={}", roomId, e);
		}
	}

	private void executeAfterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		} else {
			action.run();
		}
	}
}
