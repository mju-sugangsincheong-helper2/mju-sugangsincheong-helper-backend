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
public class DepartmentSuccessRateStatsResponse {

	private List<DepartmentRanking> rankings;
	private MyDepartmentInfo myDepartment;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DepartmentRanking {
		private int rank;
		private String department;
		private long totalCount;
		private long successCount;
		private double successRate;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MyDepartmentInfo {
		private String department;
		private long totalCount;
		private long successCount;
		private double successRate;
		private int rank;
	}
}
