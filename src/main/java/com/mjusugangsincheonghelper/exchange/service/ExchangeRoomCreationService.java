package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMemberRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ExchangeRoomCreationService {

	private final ExchangeRoomRepository roomRepository;
	private final ExchangeRoomMemberRepository roomMemberRepository;
	private final CacheManager cacheManager;

	@Transactional
	public Long createRoom(String term, List<ExchangeIntentEntity> cycle, String cycleHash) {
		ExchangeRoomEntity room = roomRepository.save(ExchangeRoomEntity.builder()
				.term(term)
				.cycleHash(cycleHash)
				.build());

		for (ExchangeIntentEntity intent : cycle) {
			roomMemberRepository.save(ExchangeRoomMemberEntity.builder()
					.term(term)
					.roomId(room.getId())
					.memberId(intent.getMemberId())
					.intentId(intent.getId())
					.build());
		}

		List<Long> memberIds = cycle.stream().map(ExchangeIntentEntity::getMemberId).toList();

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				evictParticipantCaches(term, memberIds);
			}
		});

		return room.getId();
	}

	private void evictParticipantCaches(String term, List<Long> memberIds) {
		Cache roomIdsCache = cacheManager.getCache("user-room-ids");
		Cache unreadCache = cacheManager.getCache("user-unread-counts");

		if (roomIdsCache != null) {
			for (Long memberId : memberIds) {
				roomIdsCache.evict(term + ":member:" + memberId + ":room_ids:cache");
			}
		}
		if (unreadCache != null) {
			for (Long memberId : memberIds) {
				unreadCache.evict(term + ":member:" + memberId + ":unread_counts:dto");
			}
		}
	}
}
