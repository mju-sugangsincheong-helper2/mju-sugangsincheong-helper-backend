package com.mjusugangsincheonghelper.exchange.dto.cache;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.Member;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방 단위 메타데이터 캐시 DTO (exchange-room-meta 캐시).
 *
 * <p>방의 정적 정보(cycleHash, createdAt)와 동적 정보(상태, 마지막 메시지, 참여자 목록)를 함께 담는다.
 * 방에 영향을 주는 쓰기(메시지 전송/방 토글/의도 철회)마다 evict 되므로 하나의 캐시로 관리한다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomMetaCacheDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private String cycleHash;
	private Instant createdAt;
	private String status;
	private MessageSummary lastMessage;
	private List<Participant> participants;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MessageSummary implements Serializable {

		private static final long serialVersionUID = 1L;

		private Long messageId;
		private Long senderId;
		private String messageType;
		private String content;
		private Instant createdAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Participant implements Serializable {

		private static final long serialVersionUID = 1L;

		private Long memberId;
		private String name;
		private String department;
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;

		@JsonProperty("isDeleted")
		private boolean isDeleted;

		@JsonProperty("isOn")
		private boolean isOn;
	}

	public static RoomMetaCacheDto from(
			ExchangeRoomEntity room,
			ExchangeRoomMessageEntity lastMessage,
			List<ExchangeRoomIntentEntity> roomIntents,
			Function<Long, ExchangeIntentEntity> intentLoader,
			Function<Long, Member> memberLoader) {
		MessageSummary lastMessageSummary = null;
		if (lastMessage != null) {
			lastMessageSummary = MessageSummary.builder()
					.messageId(lastMessage.getId())
					.senderId(lastMessage.getMemberId())
					.messageType(lastMessage.getMessageType())
					.content(lastMessage.getContent())
					.createdAt(lastMessage.getCreatedAt())
					.build();
		}

		List<Participant> participants = roomIntents.stream()
				.map(roomIntent -> {
					ExchangeIntentEntity intent = intentLoader.apply(roomIntent.getIntentId());
					Member member = memberLoader.apply(roomIntent.getMemberId());
					return Participant.builder()
							.memberId(roomIntent.getMemberId())
							.name(member != null ? member.getName() : null)
							.department(member != null ? member.getDepartment() : null)
							.intentId(roomIntent.getIntentId())
							.giveCourseNo(intent != null ? intent.getGiveCourseNo() : null)
							.wantCourseNo(intent != null ? intent.getWantCourseNo() : null)
							.isDeleted(roomIntent.isDeleted())
							.isOn(roomIntent.isOn())
							.build();
				})
				.toList();

		return RoomMetaCacheDto.builder()
				.cycleHash(room.getCycleHash())
				.createdAt(room.getCreatedAt())
				.status(room.getStatus())
				.lastMessage(lastMessageSummary)
				.participants(participants)
				.build();
	}
}
