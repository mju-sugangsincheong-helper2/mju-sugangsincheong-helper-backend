package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundAnalysisResponse {
	private String multigameId;
	private int participantCount;
	private int capacity;
	private Instant createdAt;
	private List<SubjectStat> subjects;

	public static RoundAnalysisResponse from(MultigameRoundEntity round, List<Object[]> aggregateRows) {
		int[] applied = new int[7];
		int[] succeeded = new int[7];
		for (Object[] row : aggregateRows) {
			int subjectId = ((Number) row[0]).intValue();
			if (subjectId < 1 || subjectId > 6) {
				continue;
			}
			applied[subjectId] = ((Number) row[1]).intValue();
			succeeded[subjectId] = ((Number) row[2]).intValue();
		}
		List<SubjectStat> subjects = new ArrayList<>();
		for (int subjectId = 1; subjectId <= 6; subjectId++) {
			double competitionRate = round.getCapacity() > 0
					? Math.round(applied[subjectId] * 10.0 / round.getCapacity()) / 10.0
					: 0;
			subjects.add(SubjectStat.builder()
					.subjectId(subjectId)
					.applied(applied[subjectId])
					.succeeded(succeeded[subjectId])
					.competitionRate(competitionRate)
					.build());
		}
		return RoundAnalysisResponse.builder()
				.multigameId(round.getStartTime())
				.participantCount(round.getParticipantCount())
				.capacity(round.getCapacity())
				.createdAt(round.getCreatedAt())
				.subjects(subjects)
				.build();
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SubjectStat {
		private int subjectId;
		private int applied;
		private int succeeded;
		private double competitionRate;
	}
}
