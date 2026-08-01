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
	private boolean isOwner;
	private boolean isMember;
	private int totalCourses;
	private int totalTime;
	private RankingSummary ranking;
	private List<BasicEvent> basic;
	private List<DetailEvent> detail;
	private FeedbacksResponse feedbacks;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RankingSummary implements Serializable {

		private static final long serialVersionUID = 1L;

		private RankDetail global;
		private RankDetail department;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RankDetail implements Serializable {

		private static final long serialVersionUID = 1L;

		private int rank;
		private int totalParticipants;
		private double percentile;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BasicEvent implements Serializable {

		private static final long serialVersionUID = 1L;

		private int sequence;
		private String type;
		private String label;
		private int durationMs;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DetailEvent implements Serializable {

		private static final long serialVersionUID = 1L;

		private int sequence;
		private String type;
		private String label;
		private int durationMs;
		private double percentile;
		private String grade;
		private PopulationStats globalPopulation;
		private PopulationStats departmentPopulation;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PopulationStats implements Serializable {

		private static final long serialVersionUID = 1L;

		private int p10;
		private int p30;
		private int p50;
		private int p70;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedbacksResponse implements Serializable {

		private static final long serialVersionUID = 1L;

		private FeedbackItem primary;
		private FeedbackItem secondary;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedbackItem implements Serializable {

		private static final long serialVersionUID = 1L;

		private String code;
		private String message;
		private String axis;
	}
}
