package com.mjusugangsincheonghelper.exchange.dto;

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
	private List<RoomItem> myRooms;
	private List<RecentIntentItem> recentIntents;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class IntentItem {
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;
		private boolean isDeleted;
		private Instant createdAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RoomItem {
		private Long roomId;
		private boolean isActive;
		private boolean isOn;
		private int unreadCount;
		private String lastMessageContent;
		private Instant lastMessageAt;
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
