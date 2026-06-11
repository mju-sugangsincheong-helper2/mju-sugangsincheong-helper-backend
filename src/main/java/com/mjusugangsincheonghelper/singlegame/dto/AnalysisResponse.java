package com.mjusugangsincheonghelper.singlegame.dto;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	private long gameId;
	private int totalCourses;
	private boolean completed;
	private AnalysisSummary summary;
	private List<AnalysisDetail> details;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AnalysisSummary implements Serializable {

		private static final long serialVersionUID = 1L;

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
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AnalysisDetail implements Serializable {

		private static final long serialVersionUID = 1L;

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
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DataBucket implements Serializable {

		private static final long serialVersionUID = 1L;

		private int clickCourse;
		private int clickYes;
		private int clickOk;
		private int total;
	}
}
