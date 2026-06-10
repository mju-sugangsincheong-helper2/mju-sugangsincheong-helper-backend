package com.mjusugangsincheonghelper.exchange.dto;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MainResponse {

	private final String message;
	private final Instant timestamp;
	private final List<IntentItem> myIntents;
	private final List<RoomSummary> myRooms;

	@Getter
	@Builder
	@AllArgsConstructor
	public static class IntentItem {
		private final Long intentId;
		private final String giveCourseNo;
		private final String wantCourseNo;
		private final boolean isDeleted;
		private final Instant createdAt;

		public static IntentItem from(ExchangeIntentEntity entity) {
			return IntentItem.builder()
					.intentId(entity.getId())
					.giveCourseNo(entity.getGiveCourseNo())
					.wantCourseNo(entity.getWantCourseNo())
					.isDeleted(entity.isDeleted())
					.createdAt(entity.getCreatedAt())
					.build();
		}
	}

	@Getter
	@Builder
	@AllArgsConstructor
	public static class RoomSummary {
		private final Long roomId;
		private final int totalParticipants;
		private final int activeIntentCount;
		private final int unreadMessageCount;
		private final String lastMessage;
		private final Instant lastMessageAt;
		private final List<CycleDetail> cycleDetails;
	}

	@Getter
	@Builder
	@AllArgsConstructor
	public static class CycleDetail {
		private final Long memberId;
		private final String giveCourseNo;
		private final String wantCourseNo;
	}
}
