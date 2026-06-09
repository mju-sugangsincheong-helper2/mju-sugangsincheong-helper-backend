package com.mjusugangsincheonghelper.singlegame.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MyRecordResponse {

	private long gameId;
	private int totalCourses;
	private boolean isCompleted;
	private int tTotal;
	private int tEnterMain;
	private Instant createdAt;
	private RecordRanking ranking;

	@Getter
	@Builder
	@AllArgsConstructor
	public static class RecordRanking {
		private RankInfo global;
		private RankInfo department;
	}

	@Getter
	@Builder
	@AllArgsConstructor
	public static class RankInfo {
		private int rank;
		private int totalParticipants;
		private double percentile;
	}
}
