package com.mjusugangsincheonghelper.singlegame.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AnalysisResponse {

	private long gameId;
	private int totalCourses;
	private boolean isCompleted;
	private AnalysisSummary summary;
	private List<AnalysisDetail> details;

	@Getter
	@Builder
	@AllArgsConstructor
	public static class AnalysisSummary {
		private int totalTime;
		private int globalRank;
		private double globalPercentile;
		private int purePhysicalAverage;
		private int entryPrecision;
		private int initialSprintSpeed;
		private double paceDeviation;
		private String feedbackCode;
		private String feedbackMessage;
	}

	@Getter
	@Builder
	@AllArgsConstructor
	public static class AnalysisDetail {
		private int sequence;
		private DataBucket mine;
		private DataBucket p10;
		private DataBucket p30;
		private DataBucket p50;
		private DataBucket p70;
		private DataBucket p100;
	}

	@Getter
	@Builder
	@AllArgsConstructor
	public static class DataBucket {
		private int clickCourse;
		private int clickYes;
		private int clickOk;
		private int total;
	}
}
