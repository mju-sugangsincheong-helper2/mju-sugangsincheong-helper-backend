package com.mjusugangsincheonghelper.singlegame.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
	private String department;
	private List<RankingEntry> rankings;
	private MyRankInfo myRank;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(name = "RankingResponseRankingEntry")
	public static class RankingEntry implements Serializable {

		private static final long serialVersionUID = 1L;

		private int rank;
		private long gameId;
		private String name;
		private String department;
		private int tTotal;
		private int tEnterMain;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(name = "RankingResponseMyRankInfo")
	public static class MyRankInfo implements Serializable {

		private static final long serialVersionUID = 1L;

		private int rank;
		private long gameId;
		private int tTotal;
		private int tEnterMain;
	}
}


