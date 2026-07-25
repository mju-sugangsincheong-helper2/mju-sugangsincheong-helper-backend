package com.mjusugangsincheonghelper.multigame.stats.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyStatsResponse {

	private long totalGames;
	private long successCount;
	private long failSoldoutCount;
	private long failDuplicateCount;
	private double successRate;
	private Integer mostRequestedSubject;
	private List<SubjectBreakdown> subjectBreakdown;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SubjectBreakdown {
		private int subjectId;
		private long count;
		private long success;
	}
}
