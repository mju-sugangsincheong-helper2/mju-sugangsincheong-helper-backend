package com.mjusugangsincheonghelper.singlegame.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RankingResponse {

	private int totalCourses;
	private String scope;
	private List<RankingEntry> rankings;
	private MyRankInfo myRank;
	private SubRankings subRankings;

	@Getter
	@Builder
	@AllArgsConstructor
	public static class RankingEntry {
		private int rank;
		private long gameId;
		private String name;
		private String department;
		private int tTotal;
		private int tEnterMain;
	}

	@Getter
	@Builder
	@AllArgsConstructor
	public static class MyRankInfo {
		private int rank;
		private long gameId;
		private int tTotal;
		private int tEnterMain;
	}

	@Getter
	@Builder
	@AllArgsConstructor
	public static class SubRankings {
		private List<SubEntry> enterMainTop3;
		private List<SubEntry> firstClickTop3;
	}

	@Getter
	@Builder
	@AllArgsConstructor
	public static class SubEntry {
		private int rank;
		private String name;
		private int tEnterMain;
		private int tClickCourse1st;
	}
}
