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
public class RecentIntentsResponse {

	private List<IntentFeedItem> recentIntents;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class IntentFeedItem {
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;
		private Instant createdAt;
	}
}
