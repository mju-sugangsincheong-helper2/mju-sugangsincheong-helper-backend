package com.mjusugangsincheonghelper.exchange.event;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.exchange.dto.CycleDetectionMessage;
import com.mjusugangsincheonghelper.exchange.service.ExchangeCacheService;
import com.mjusugangsincheonghelper.exchange.service.ExchangeCycleDetector;
import com.mjusugangsincheonghelper.notification.publisher.NotificationPublisher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 교환 쓰기 부작용의 유일한 응집점.
 *
 * <p>{@link ExchangeEvents}를 커밋 후(AFTER_COMMIT)에 처리한다. 캐시 evict,
 * 사이클 탐지 큐 적재, Firebase Cloud Messaging 발행은 전부 트랜잭션이 성공적으로 커밋된 뒤에야
 * 일어나므로 롤백된 데이터에 대한 부작용이 없다.
 *
 * <p>참고: 트랜잭션 밖에서 발행된 이벤트는 전달되지 않는다. 교환 쓰기 경로는
 * 전부 {@code @Transactional} 안에서만 이벤트를 발행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeEventListener {

	private final ExchangeCacheService cacheService;
	private final ExchangeCycleDetector cycleDetector;
	private final ExchangeRoomIntentRepository roomIntentRepository;
	private final NotificationPublisher notificationPublisher;

	@TransactionalEventListener
	public void onIntentCreated(ExchangeEvents.IntentCreated event) {
		cacheService.evictFeed(event.term());
		cacheService.evictMainCache(event.term(), event.memberId());
		cacheService.evictMemberIntents(event.term(), event.memberId());
		cycleDetector.enqueueCycleDetection(CycleDetectionMessage.builder()
				.term(event.term())
				.intentId(event.intentId())
				.memberId(event.memberId())
				.giveCourseNo(event.giveCourseNo())
				.wantCourseNo(event.wantCourseNo())
				.build());
	}

	@TransactionalEventListener
	public void onIntentDeleted(ExchangeEvents.IntentDeleted event) {
		cacheService.evictFeed(event.term());
		cacheService.evictMemberIntents(event.term(), event.memberId());
		for (Long roomId : event.roomIds()) {
			cacheService.evictRoomMeta(event.term(), roomId);
		}
		// 철회자 본인은 방 참여 여부와 무관하게 항상 evict
		cacheService.evictMainCache(event.term(), event.memberId());
		for (Long memberId : event.memberIds()) {
			if (!memberId.equals(event.memberId())) {
				cacheService.evictMainCache(event.term(), memberId);
			}
		}
	}

	@TransactionalEventListener
	public void onRoomCreated(ExchangeEvents.RoomCreated event) {
		for (Long memberId : event.memberIds()) {
			cacheService.evictMainCache(event.term(), memberId);
		}
		notificationPublisher.publishToMembers(event.memberIds(), "EXCHANGE_ROOM",
				"/exchange/rooms/" + event.roomId(), "수강신청 교환 매칭 성공", "[시스템] 교환 매칭이 성사되었습니다!");
	}

	@TransactionalEventListener
	public void onRoomMessageSent(ExchangeEvents.RoomMessageSent event) {
		cacheService.evictRoomMeta(event.term(), event.roomId());

		List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(event.term(), event.roomId());
		roomIntents.stream()
				.map(ExchangeRoomIntentEntity::getMemberId)
				.distinct()
				.forEach(memberId -> cacheService.evictMainCache(event.term(), memberId));

		List<Long> targetMemberIds = roomIntents.stream()
				.filter(ri -> ri.isOn() && !ri.isDeleted() && !ri.getMemberId().equals(event.senderMemberId()))
				.map(ExchangeRoomIntentEntity::getMemberId)
				.distinct()
				.toList();
		if (targetMemberIds.isEmpty()) {
			log.debug("No active target members to receive Firebase Cloud Messaging notification. roomId={}", event.roomId());
			return;
		}
		notificationPublisher.publishToMembers(targetMemberIds, "EXCHANGE_MESSAGE",
				"/exchange/rooms/" + event.roomId(), "수강신청 교환 대화방 메시지", event.content());
	}

	@TransactionalEventListener
	public void onRoomToggled(ExchangeEvents.RoomToggled event) {
		cacheService.evictRoomMeta(event.term(), event.roomId());
		roomIntentRepository.findDistinctMemberIdsByTermAndRoomId(event.term(), event.roomId())
				.forEach(memberId -> cacheService.evictMainCache(event.term(), memberId));
	}

	@TransactionalEventListener
	public void onRoomViewed(ExchangeEvents.RoomViewed event) {
		cacheService.evictMainCache(event.term(), event.memberId());
	}
}
