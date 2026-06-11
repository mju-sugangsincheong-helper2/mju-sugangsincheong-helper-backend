package com.mjusugangsincheonghelper.singlegame.dto.cache;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordCacheDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private long gameId;
	private int totalCourses;
	private boolean completed;
	private int tTotal;
	private int tEnterMain;
	private Instant createdAt;
	private RecordRanking ranking;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RecordRanking implements Serializable {

		private static final long serialVersionUID = 1L;

		private RankInfo global;
		private RankInfo department;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RankInfo implements Serializable {

		private static final long serialVersionUID = 1L;

		private int rank;
		private int totalParticipants;
		private double percentile;
	}
}
