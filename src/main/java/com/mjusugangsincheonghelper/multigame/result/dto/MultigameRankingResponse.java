package com.mjusugangsincheonghelper.multigame.result.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultigameRankingResponse {
	private List<ParticipationEntry> participation;
	private List<PerformanceEntry> performance;
	private MyDepartment myDepartment;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ParticipationEntry {
		private String department;
		private int participantCount;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PerformanceEntry {
		private String department;
		private double top70AvgSuccessRate;
		private int participantCount;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MyDepartment {
		private String department;
		private int participationRank;
		private int performanceRank;
		private int participantCount;
		private double top70AvgSuccessRate;
	}
}
