package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMemberRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadRepository;
import com.mjusugangsincheonghelper.exchange.dto.cache.IntentDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeUserCacheService {

	private final ExchangeIntentRepository intentRepository;
	private final ExchangeRoomMemberRepository roomMemberRepository;
	private final ExchangeRoomReadRepository roomReadRepository;
	private final ExchangeMessageRepository messageRepository;

	@Cacheable(value = "user-intents", key = "#term + ':member:' + #memberId + ':intents:cache'", sync = true)
	public List<IntentDto> getUserIntents(String term, Long memberId) {
		return intentRepository.findByTermAndMemberIdOrderByIdDesc(term, memberId).stream()
				.map(IntentDto::from)
				.toList();
	}

	@Cacheable(value = "user-room-ids", key = "#term + ':member:' + #memberId + ':room_ids:cache'", sync = true)
	public List<Long> getUserRoomIds(String term, Long memberId) {
		return roomMemberRepository.findByTermAndMemberId(term, memberId).stream()
				.map(ExchangeRoomMemberEntity::getRoomId)
				.distinct()
				.toList();
	}

	@Cacheable(value = "user-unread-counts", key = "#term + ':member:' + #memberId + ':unread_counts:cache'", sync = true)
	public Map<Long, Integer> getUserUnreadCounts(String term, Long memberId) {
		List<ExchangeRoomMemberEntity> roomMembers = roomMemberRepository.findByTermAndMemberId(term, memberId);
		Map<Long, Integer> counts = new HashMap<>();
		for (ExchangeRoomMemberEntity rm : roomMembers) {
			ExchangeRoomReadEntity read = roomReadRepository.findById(
					new ExchangeRoomReadEntity.ExchangeRoomReadId(term, rm.getRoomId(), memberId)
			).orElse(null);
			Long lastReadId = read != null ? read.getLastReadMessageId() : 0L;
			int count = messageRepository.countByTermAndRoomIdAndIdGreaterThan(term, rm.getRoomId(), lastReadId);
			counts.put(rm.getRoomId(), count);
		}
		return counts;
	}
}
