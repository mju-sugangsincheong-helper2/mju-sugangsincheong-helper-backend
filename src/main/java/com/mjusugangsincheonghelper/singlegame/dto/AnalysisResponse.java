package com.mjusugangsincheonghelper.singlegame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게임 분석 응답. 5블록: 판(record) + 순위(global/department) + 구간별 성적(detail) + 피드백.
 * 붙는 통계·순위는 전부 판 주인 기준. 요청자로 따지는 건 isOwner뿐.
 * 게스트 조회에서는 departmentRank·feedbacks·grade·departmentPopulation이 null이다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	private long gameId;

	@JsonProperty("isOwner")
	private boolean isOwner;

	@JsonProperty("isMember")
	private boolean isMember;

	private int totalCourses;
	private RecordInfo record;
	private RankInfo globalRank;
	private DeptRankInfo departmentRank;
	private List<TimelineEvent> globalTimeline;
	private List<TimelineEvent> departmentTimeline;
	private List<DetailEvent> detail;
	private FeedbacksResponse feedbacks;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RecordInfo implements Serializable {

		private static final long serialVersionUID = 1L;

		private int tTotal;
		private int tEnterMain;
		private boolean completed;
		private Instant createdAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RankInfo implements Serializable {

		private static final long serialVersionUID = 1L;

		private int rank;
		private long totalPersons;
		private double percentile;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DeptRankInfo implements Serializable {

		private static final long serialVersionUID = 1L;

		private String department;
		private int rank;
		private long totalPersons;
		private double percentile;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TimelineEvent implements Serializable {

		private static final long serialVersionUID = 1L;

		private int sequence;
		private String type;
		private String label;
		private int durationMs;
		private PopulationStats population;
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
