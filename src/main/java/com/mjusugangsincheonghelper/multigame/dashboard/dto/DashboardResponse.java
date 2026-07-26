package com.mjusugangsincheonghelper.multigame.dashboard.dto;

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
public class DashboardResponse {

	private List<RecentGame> recentGames;
	private List<MyRecentResult> myRecentResults;
	private OverallStats overallStats;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RecentGame {
		private String multigameId;
		private int participantCount;
		private int capacity;
		private Instant finalizedAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MyRecentResult {
		private String multigameId;
		private int subjectId;
		private String status;
		private Instant finalizedAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OverallStats {
		private long totalGames;
		private long totalParticipants;
		private double averageParticipants;
	}
}
