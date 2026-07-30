package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadStatusEntity;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notification.consumer.NotificationConsumerWorker;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRoomCreationService {

	private final EntityManager entityManager;
	private final ExchangeRoomRepository roomRepository;
	private final ExchangeRoomIntentRepository roomIntentRepository;
	private final ExchangeRoomMessageRepository messageRepository;
	private final ExchangeRoomReadStatusRepository readStatusRepository;
	private final MemberDeviceRepository memberDeviceRepository;
	private final PgmqService pgmqService;
	private final ExchangeCacheService cacheService;

	@Transactional
	public Long createRoom(String term, List<ExchangeIntentEntity> cycle, String cycleHash) {
		List<ExchangeIntentEntity> sortedCycle = cycle.stream()
				.sorted(Comparator.comparingLong(ExchangeIntentEntity::getId))
				.toList();

		for (ExchangeIntentEntity intent : sortedCycle) {
			ExchangeIntentEntity locked = entityManager.find(
					ExchangeIntentEntity.class,
					new ExchangeIntentEntity.ExchangeIntentId(term, intent.getId()),
					jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
			);
			if (locked == null || locked.isDeleted()) {
				log.warn("Intent {} was deleted before room creation, skipping cycle", intent.getId());
				return null;
			}
		}

		if (roomRepository.findByTermAndCycleHash(term, cycleHash).isPresent()) {
			return null;
		}

		ExchangeRoomEntity room = roomRepository.save(ExchangeRoomEntity.builder()
				.term(term)
				.cycleHash(cycleHash)
				.status("ACTIVE")
				.build());

		for (ExchangeIntentEntity intent : cycle) {
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term)
					.roomId(room.getId())
					.intentId(intent.getId())
					.memberId(intent.getMemberId())
					.build());
		}

		List<ExchangeIntentEntity> distinctMemberIntents = cycle.stream()
				.collect(java.util.stream.Collectors.toMap(ExchangeIntentEntity::getMemberId, i -> i, (i1, i2) -> i1, java.util.LinkedHashMap::new))
				.values().stream().toList();

		for (ExchangeIntentEntity intent : distinctMemberIntents) {
			readStatusRepository.save(ExchangeRoomReadStatusEntity.builder()
					.term(term)
					.roomId(room.getId())
					.memberId(intent.getMemberId())
					.intentId(intent.getId())
					.build());
		}

		String welcomeContent = buildWelcomeMessage(cycle);
		ExchangeRoomMessageEntity welcomeMsg = messageRepository.save(ExchangeRoomMessageEntity.builder()
				.term(term)
				.roomId(room.getId())
				.memberId(null)
				.intentId(null)
				.messageType("SYSTEM")
				.content(welcomeContent)
				.build());

		for (ExchangeIntentEntity intent : distinctMemberIntents) {
			ExchangeRoomReadStatusEntity read = readStatusRepository.findById(
					new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), intent.getMemberId())
			).orElseThrow();
			read.updateLastReadMessageId(welcomeMsg.getId());
		}

		List<Long> memberIds = distinctMemberIntents.stream().map(ExchangeIntentEntity::getMemberId).toList();

		executeAfterCommit(() -> {
			for (Long memberId : memberIds) {
				cacheService.evictMainCache(term, memberId);
			}
			sendFcmForRoomCreated(term, room.getId(), memberIds);
		});

		return room.getId();
	}

	private void sendFcmForRoomCreated(String term, Long roomId, List<Long> memberIds) {
		try {
			String path = "/exchange/rooms/" + roomId;
			String timestamp = String.valueOf(System.currentTimeMillis());

			for (Long targetMemberId : memberIds) {
				List<MemberDevice> devices = memberDeviceRepository.findByMemberId(targetMemberId);
				for (MemberDevice device : devices) {
					String token = device.getFcmToken();
					if (token != null && !token.isBlank()) {
						NotificationEventMessage event = NotificationEventMessage.builder()
								.token(token)
								.notification(NotificationEventMessage.NotificationPayload.builder()
										.title("수강신청 교환 매칭 성공")
										.body("[시스템] 교환 매칭이 성사되었습니다!")
										.build())
								.data(Map.of(
										"type", "EXCHANGE_ROOM",
										"path", path,
										"timestamp", timestamp
								))
								.build();
						pgmqService.send(NotificationConsumerWorker.QUEUE_NAME, event);
						log.info("Queued FCM notification for EXCHANGE_ROOM: memberId={}, deviceId={}, path={}",
								targetMemberId, device.getId(), path);
					}
				}
			}
		} catch (Exception e) {
			log.warn("FCM 방 생성 알림 발송 중 오류 발생: roomId={}", roomId, e);
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

	private String buildWelcomeMessage(List<ExchangeIntentEntity> cycle) {
		StringBuilder sb = new StringBuilder();
		sb.append("[시스템] 교환 매칭이 성사되었습니다!\n");
		sb.append("참여자의 교환 의사:\n");
		for (ExchangeIntentEntity intent : cycle) {
			sb.append("  - ").append(intent.getGiveCourseNo()).append(" → ").append(intent.getWantCourseNo()).append("\n");
		}
		return sb.toString();
	}
}
