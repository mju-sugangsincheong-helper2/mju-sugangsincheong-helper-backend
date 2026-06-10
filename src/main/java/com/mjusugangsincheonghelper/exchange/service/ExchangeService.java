package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMemberRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.CycleDetail;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.IntentItem;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.RoomSummary;
import com.mjusugangsincheonghelper.exchange.dto.MessageResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageResponse.MessageItem;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendRequest;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse.IntentFeedItem;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeService {

	private final ExchangeIntentRepository intentRepository;
	private final ExchangeRoomRepository roomRepository;
	private final ExchangeRoomMemberRepository roomMemberRepository;
	private final ExchangeMessageRepository messageRepository;
	private final ExchangeRoomReadRepository roomReadRepository;
	private final ExchangeCycleDetector cycleDetector;
	private final ExchangeRedisService redisService;
	private final SystemConfigService systemConfigService;

	@Transactional
	public IntentCreateResponse createIntent(Long memberId, IntentCreateRequest request) {
		String term = systemConfigService.getCurrentTerm();

		if (request.getGiveCourseNo().equals(request.getWantCourseNo())) {
			throw new BaseException(ErrorCode.EXCHANGE_SAME_COURSE);
		}

		List<ExchangeIntentEntity> myIntents = intentRepository.findByTermAndMemberIdOrderByIdDesc(term, memberId);
		boolean duplicate = myIntents.stream()
				.filter(i -> !i.isDeleted())
				.anyMatch(i -> i.getGiveCourseNo().equals(request.getGiveCourseNo())
						&& i.getWantCourseNo().equals(request.getWantCourseNo()));
		if (duplicate) {
			throw new BaseException(ErrorCode.EXCHANGE_DUPLICATE_INTENT);
		}

		ExchangeIntentEntity saved = intentRepository.save(ExchangeIntentEntity.builder()
				.term(term)
				.memberId(memberId)
				.giveCourseNo(request.getGiveCourseNo())
				.wantCourseNo(request.getWantCourseNo())
				.build());

		List<List<ExchangeIntentEntity>> cycles = cycleDetector.detectCycles(term, saved);
		for (List<ExchangeIntentEntity> cycle : cycles) {
			String cycleHash = cycleDetector.computeCycleHash(cycle);
			if (roomRepository.findByTermAndCycleHash(term, cycleHash).isEmpty()) {
				createRoom(term, cycle, cycleHash);
			}
		}

		redisService.addIntentToFeed(term, saved.getId(), saved.getGiveCourseNo(), saved.getWantCourseNo(), saved.getCreatedAt().toString());
		redisService.addGraphEdge(term, memberId, saved.getGiveCourseNo(), saved.getWantCourseNo());

		return IntentCreateResponse.from(saved);
	}

	@Transactional
	public void createRoom(String term, List<ExchangeIntentEntity> cycle, String cycleHash) {
		ExchangeRoomEntity room = roomRepository.save(ExchangeRoomEntity.builder()
				.term(term)
				.cycleHash(cycleHash)
				.build());

		List<CycleDetail> cycleDetails = new ArrayList<>();
		for (ExchangeIntentEntity intent : cycle) {
			roomMemberRepository.save(ExchangeRoomMemberEntity.builder()
					.term(term)
					.roomId(room.getId())
					.memberId(intent.getMemberId())
					.intentId(intent.getId())
					.build());
			cycleDetails.add(CycleDetail.builder()
					.memberId(intent.getMemberId())
					.giveCourseNo(intent.getGiveCourseNo())
					.wantCourseNo(intent.getWantCourseNo())
					.build());
		}

		RoomSummary summary = RoomSummary.builder()
				.roomId(room.getId())
				.totalParticipants(cycle.size())
				.activeIntentCount(cycle.size())
				.unreadMessageCount(0)
				.lastMessage(null)
				.lastMessageAt(null)
				.cycleDetails(cycleDetails)
				.build();
		redisService.setRoomSummary(term, summary, cycleDetails);

		for (ExchangeIntentEntity intent : cycle) {
			redisService.addRoomToMember(term, intent.getMemberId(), room.getId());
		}

		redisService.markCycleHash(term, cycleHash);
	}

	@Transactional
	public IntentDeleteResponse deleteIntent(Long memberId, Long intentId) {
		String term = systemConfigService.getCurrentTerm();

		ExchangeIntentEntity intent = intentRepository.findById(new com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity.ExchangeIntentId(term, intentId))
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_FOUND));

		if (!intent.getMemberId().equals(memberId)) {
			throw new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_OWNER);
		}

		if (intent.isDeleted()) {
			throw new BaseException(ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED);
		}

		intent.delete();

		redisService.removeIntentFromFeed(term, intentId);
		redisService.removeGraphEdge(term, memberId, intent.getGiveCourseNo(), intent.getWantCourseNo());

		return IntentDeleteResponse.builder()
				.message("교환 의사가 철회되었습니다.")
				.timestamp(Instant.now())
				.intentId(intentId)
				.isDeleted(true)
				.build();
	}

	public MainResponse getMain(Long memberId) {
		String term = systemConfigService.getCurrentTerm();

		List<IntentItem> myIntents = intentRepository.findByTermAndMemberIdOrderByIdDesc(term, memberId).stream()
				.map(IntentItem::from)
				.collect(Collectors.toList());

		List<RoomSummary> myRooms = new ArrayList<>();
		for (String roomIdStr : redisService.getMemberRooms(term, memberId)) {
			Long roomId = Long.parseLong(roomIdStr);
			RoomSummary cachedSummary = redisService.getRoomSummary(term, roomId);
			if (cachedSummary == null) {
				continue;
			}

			int unread = redisService.getUnreadCount(term, memberId, roomId);
			List<CycleDetail> cycleDetails = redisService.getCycleDetails(term, roomId);

			String lastMessage = cachedSummary.getLastMessage();
			Instant lastMessageAt = cachedSummary.getLastMessageAt();

			var latestMsg = messageRepository.findTopByTermAndRoomIdOrderByIdDesc(term, roomId);
			if (latestMsg.isPresent() && lastMessage == null) {
				var msg = latestMsg.get();
				lastMessage = msg.getContent();
				lastMessageAt = msg.getCreatedAt();
			}

			myRooms.add(RoomSummary.builder()
					.roomId(cachedSummary.getRoomId())
					.totalParticipants(cachedSummary.getTotalParticipants())
					.activeIntentCount(cachedSummary.getActiveIntentCount())
					.unreadMessageCount(unread)
					.lastMessage(lastMessage)
					.lastMessageAt(lastMessageAt)
					.cycleDetails(cycleDetails)
					.build());
		}

		return MainResponse.builder()
				.message("메인 상태 조회 성공")
				.timestamp(Instant.now())
				.myIntents(myIntents)
				.myRooms(myRooms)
				.build();
	}

	public RecentIntentsResponse getRecentIntents(Long lastIntentId, int limit) {
		String term = systemConfigService.getCurrentTerm();
		PageRequest pageable = PageRequest.of(0, limit + 1);
		List<ExchangeIntentEntity> intents = intentRepository.findByTermAndIdGreaterThanOrderByIdAsc(term, lastIntentId, pageable);

		boolean hasNext = intents.size() > limit;
		List<ExchangeIntentEntity> result = hasNext ? intents.subList(0, limit) : intents;

		List<IntentFeedItem> items = result.stream()
				.map(IntentFeedItem::from)
				.collect(Collectors.toList());

		Long nextLastIntentId = result.isEmpty() ? lastIntentId : result.get(result.size() - 1).getId();

		return RecentIntentsResponse.builder()
				.message("최근 등록된 교환 의사 조회 성공")
				.timestamp(Instant.now())
				.intents(items)
				.nextLastIntentId(nextLastIntentId)
				.hasNext(hasNext)
				.build();
	}

	@Transactional
	public MessageResponse getMessages(Long memberId, Long roomId, Long lastMessageId, int size) {
		String term = systemConfigService.getCurrentTerm();

		roomMemberRepository.findById(new com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity.ExchangeRoomMemberId(term, roomId, memberId))
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER));

		PageRequest pageable = PageRequest.of(0, size + 1);
		List<ExchangeMessageEntity> messages = messageRepository.findByTermAndRoomIdAndIdLessThanOrderByIdDesc(term, roomId, lastMessageId, pageable);

		boolean hasNext = messages.size() > size;
		List<ExchangeMessageEntity> result = hasNext ? messages.subList(0, size) : messages;

		List<MessageItem> items = result.stream()
				.map(MessageItem::from)
				.collect(Collectors.toList());

		if (!result.isEmpty()) {
			Long minId = result.stream().mapToLong(ExchangeMessageEntity::getId).min().orElse(0L);
			ExchangeRoomReadEntity read = roomReadRepository.findById(new com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadEntity.ExchangeRoomReadId(term, roomId, memberId))
					.orElse(ExchangeRoomReadEntity.builder().term(term).roomId(roomId).memberId(memberId).build());
			read.updateLastReadMessageId(minId);
			roomReadRepository.save(read);
			redisService.clearUnread(term, memberId, roomId);
		}

		Long nextLastMessageId = result.isEmpty() ? lastMessageId : result.get(result.size() - 1).getId();

		return MessageResponse.builder()
				.message("메시지 조회 성공")
				.timestamp(Instant.now())
				.roomId(roomId)
				.messages(items)
				.nextLastMessageId(nextLastMessageId)
				.hasNext(hasNext)
				.build();
	}

	@Transactional
	public MessageSendResponse sendMessage(Long memberId, Long roomId, MessageSendRequest request) {
		String term = systemConfigService.getCurrentTerm();

		roomMemberRepository.findById(new com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity.ExchangeRoomMemberId(term, roomId, memberId))
				.orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER));

		ExchangeMessageEntity saved = messageRepository.save(ExchangeMessageEntity.builder()
				.term(term)
				.roomId(roomId)
				.senderId(memberId)
				.content(request.getContent())
				.build());

		for (ExchangeRoomMemberEntity member : roomMemberRepository.findByTermAndRoomId(term, roomId)) {
			if (!member.getMemberId().equals(memberId)) {
				redisService.incrementUnread(term, member.getMemberId(), roomId);
			}
		}

		return MessageSendResponse.from(saved);
	}
}
