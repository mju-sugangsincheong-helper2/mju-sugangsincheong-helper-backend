package com.mjusugangsincheonghelper.singlegame.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class RankingResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	private int totalCourses;
	private String scope;
	private List<RankingEntry> rankings;
	private MyRankInfo myRank;
	private SubRankings subRankings;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE)
	public static class RankingEntry implements Serializable {

		private static final long serialVersionUID = 1L;

		private int rank;
		private long gameId;
		private String name;
		private String department;
		@JsonProperty("tTotal")
		private int tTotal;
		@JsonProperty("tEnterMain")
		private int tEnterMain;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE)
	public static class MyRankInfo implements Serializable {

		private static final long serialVersionUID = 1L;

		private int rank;
		private long gameId;
		@JsonProperty("tTotal")
		private int tTotal;
		@JsonProperty("tEnterMain")
		private int tEnterMain;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SubRankings implements Serializable {

		private static final long serialVersionUID = 1L;

		private List<SubEntry> enterMainTop3;
		private List<SubEntry> firstClickTop3;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE)
	public static class SubEntry implements Serializable {

		private static final long serialVersionUID = 1L;

		private int rank;
		private String name;
		@JsonProperty("tEnterMain")
		private int tEnterMain;
		@JsonProperty("tClickCourse1st")
		private int tClickCourse1st;
	}
}
