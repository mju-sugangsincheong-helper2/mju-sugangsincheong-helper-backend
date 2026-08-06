package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadStatusEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.exchange.event.ExchangeEvents;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRoomCreationService {

	private final EntityManager entityManager;
	private final ExchangeRoomRepository roomRepository;
	private final ExchangeRoomIntentRepository roomIntentRepository;
	private final ExchangeRoomMessageRepository messageRepository;
	private final ExchangeRoomReadStatusRepository readStatusRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public Long createRoom(String term, List<ExchangeIntentEntity> cycle, String cycleHash) {
		List<ExchangeIntentEntity> sortedCycle = cycle.stream()
				.sorted(Comparator.comparingLong(ExchangeIntentEntity::getId))
				.toList();

		for (ExchangeIntentEntity intent : sortedCycle) {
			ExchangeIntentEntity locked = entityManager.find(
					ExchangeIntentEntity.class,
					new ExchangeIntentEntity.ExchangeIntentId(intent.getTerm(), intent.getId()),
					jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
			);
			if (locked == null || locked.isDeleted()) {
				log.debug("Intent was deleted before room creation, skipping cycle. intentId={}", intent.getId());
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

		eventPublisher.publishEvent(new ExchangeEvents.RoomCreated(term, room.getId(), memberIds));

		log.info("Exchange match created. term={}, roomId={}, cycleSize={}, memberIds={}",
				term, room.getId(), cycle.size(), memberIds);

		return room.getId();
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
