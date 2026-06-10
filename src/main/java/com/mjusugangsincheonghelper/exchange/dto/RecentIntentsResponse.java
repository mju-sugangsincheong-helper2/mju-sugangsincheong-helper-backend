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
public class RecentIntentsResponse {

	private final String message;
	private final Instant timestamp;
	private final List<IntentFeedItem> intents;
	private final Long nextLastIntentId;
	private final boolean hasNext;

	@Getter
	@Builder
	@AllArgsConstructor
	public static class IntentFeedItem {
		private final Long intentId;
		private final String giveCourseNo;
		private final String wantCourseNo;
		private final Instant createdAt;

		public static IntentFeedItem from(ExchangeIntentEntity entity) {
			return IntentFeedItem.builder()
					.intentId(entity.getId())
					.giveCourseNo(entity.getGiveCourseNo())
					.wantCourseNo(entity.getWantCourseNo())
					.createdAt(entity.getCreatedAt())
					.build();
		}
	}
}
