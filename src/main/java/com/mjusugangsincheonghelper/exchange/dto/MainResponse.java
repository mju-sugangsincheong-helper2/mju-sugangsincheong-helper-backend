package com.mjusugangsincheonghelper.exchange.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
	public static class IntentItem {
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;

		@JsonProperty("isDeleted")
		private boolean isDeleted;

		private Instant createdAt;
		private List<RoomItem> rooms;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RoomItem {
		private Long roomId;
		private String term;
		private String cycleHash;
		private String status;

		@JsonProperty("isOn")
		private boolean isOn;

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
	public static class ParticipantItem {
		private Long memberId;
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;

		@JsonProperty("isDeleted")
		private boolean isDeleted;

		@JsonProperty("isOn")
		private boolean isOn;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RecentIntentItem {
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;
		private Instant createdAt;
	}
}
