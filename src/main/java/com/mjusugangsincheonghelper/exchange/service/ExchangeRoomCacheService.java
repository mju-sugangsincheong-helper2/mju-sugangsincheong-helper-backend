package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMemberRepository;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomActiveIntentsDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomDynamicMetaDto;
import com.mjusugangsincheonghelper.exchange.dto.cache.RoomStaticMetaDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeRoomCacheService {

	private final ExchangeRoomMemberRepository roomMemberRepository;
	private final ExchangeIntentRepository intentRepository;
	private final ExchangeMessageRepository messageRepository;

	@Cacheable(value = "room-static-meta", key = "#term + ':room:' + #roomId + ':static_meta:cache'", sync = true)
	public RoomStaticMetaDto getRoomStaticMeta(String term, Long roomId) {
		List<ExchangeRoomMemberEntity> members = roomMemberRepository.findByTermAndRoomId(term, roomId);
		List<RoomStaticMetaDto.CycleDetailDto> cycleDetails = members.stream()
				.map(m -> {
					ExchangeIntentEntity intent = intentRepository.findById(
							new ExchangeIntentEntity.ExchangeIntentId(term, m.getIntentId())
					).orElse(null);
					return RoomStaticMetaDto.CycleDetailDto.builder()
							.memberId(m.getMemberId())
							.giveCourseNo(intent != null ? intent.getGiveCourseNo() : "")
							.wantCourseNo(intent != null ? intent.getWantCourseNo() : "")
							.build();
				})
				.toList();

		return RoomStaticMetaDto.builder()
				.roomId(roomId)
				.totalParticipants(members.size())
				.cycleDetails(cycleDetails)
				.build();
	}

	@Cacheable(value = "room-dynamic-meta", key = "#term + ':room:' + #roomId + ':dynamic_meta:cache'", sync = true)
	public RoomDynamicMetaDto getRoomDynamicMeta(String term, Long roomId) {
		return messageRepository.findTopByTermAndRoomIdOrderByIdDesc(term, roomId)
				.map(msg -> RoomDynamicMetaDto.builder()
						.lastMessage(msg.getContent())
						.lastMessageAt(msg.getCreatedAt())
						.build())
				.orElse(RoomDynamicMetaDto.builder()
						.lastMessage(null)
						.lastMessageAt(null)
						.build());
	}

	@Cacheable(value = "room-active-intents", key = "#term + ':room:' + #roomId + ':active_intents:cache'", sync = true)
	public RoomActiveIntentsDto getRoomActiveIntents(String term, Long roomId) {
		List<ExchangeRoomMemberEntity> members = roomMemberRepository.findByTermAndRoomId(term, roomId);
		List<RoomActiveIntentsDto.ActiveIntent> intents = members.stream()
				.map(m -> {
					ExchangeIntentEntity intent = intentRepository.findById(
							new ExchangeIntentEntity.ExchangeIntentId(term, m.getIntentId())
					).orElse(null);
					return RoomActiveIntentsDto.ActiveIntent.builder()
							.intentId(m.getIntentId())
							.memberId(m.getMemberId())
							.isDeleted(intent == null || intent.isDeleted())
							.build();
				})
				.toList();

		return RoomActiveIntentsDto.builder()
				.intents(intents)
				.build();
	}
}
