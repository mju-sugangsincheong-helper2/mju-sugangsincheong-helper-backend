package com.mjusugangsincheonghelper.singlegame.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class MyRecordResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	private long gameId;
	private int totalCourses;
	private boolean completed;
	@JsonProperty("tTotal")
	private int tTotal;
	@JsonProperty("tEnterMain")
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
