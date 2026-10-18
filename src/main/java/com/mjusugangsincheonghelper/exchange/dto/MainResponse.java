package com.mjusugangsincheonghelper.exchange.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MainResponse {

	private List<IntentItem> myIntents;
	private List<RecentIntentItem> recentIntents;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(name = "MainResponseIntentItem")
	public static class IntentItem {
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;
		private boolean deleted;

		private Instant createdAt;
		private List<RoomItem> rooms;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(name = "MainResponseRoomItem")
	public static class RoomItem {
		private Long roomId;
		private String term;
		private String cycleHash;
		private String status;
		private boolean on;

		private int unreadCount;
		private Long lastReadMessageId;
		private MessageSummaryItem lastMessage;
		private List<ParticipantItem> participants;
		private Instant createdAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(name = "MainResponseMessageSummaryItem")
	public static class MessageSummaryItem {
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
	@Schema(name = "MainResponseParticipantItem")
	public static class ParticipantItem {
		private Long memberId;
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;
		private boolean deleted;
		private boolean on;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(name = "MainResponseRecentIntentItem")
	public static class RecentIntentItem {
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;
		private Instant createdAt;
	}
}
