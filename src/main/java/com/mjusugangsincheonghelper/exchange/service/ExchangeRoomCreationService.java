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
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
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
				.isActive(true)
				.build());

		for (ExchangeIntentEntity intent : cycle) {
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term)
					.roomId(room.getId())
					.intentId(intent.getId())
					.memberId(intent.getMemberId())
					.build());
		}

		for (ExchangeIntentEntity intent : cycle) {
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
				.memberId(cycle.get(0).getMemberId())
				.intentId(cycle.get(0).getId())
				.content(welcomeContent)
				.build());

		for (ExchangeIntentEntity intent : cycle) {
			ExchangeRoomReadStatusEntity read = readStatusRepository.findById(
					new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), intent.getMemberId())
			).orElseThrow();
			read.updateLastReadMessageId(welcomeMsg.getId());
		}

		List<Long> memberIds = cycle.stream().map(ExchangeIntentEntity::getMemberId).toList();

		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					for (Long memberId : memberIds) {
						cacheService.evictRooms(term, memberId);
					}
				}
			});
		} else {
			for (Long memberId : memberIds) {
				cacheService.evictRooms(term, memberId);
			}
		}

		return room.getId();
	}

	private String buildWelcomeMessage(List<ExchangeIntentEntity> cycle) {
		StringBuilder sb = new StringBuilder();
		sb.append("[시스템] 교환 매칭이 성사되었습니다!\n");
		sb.append("참여자의 교환 의사:\n");
		for (ExchangeIntentEntity intent : cycle) {
			sb.append("  - ").append(intent.getGiveCourseNo()).append(" → ").append(intent.getWantCourseNo()).append("\n");
		}
		sb.append("채팅을 통해 교환 시점과 방법을 협의해주세요.");
		return sb.toString();
	}
}
