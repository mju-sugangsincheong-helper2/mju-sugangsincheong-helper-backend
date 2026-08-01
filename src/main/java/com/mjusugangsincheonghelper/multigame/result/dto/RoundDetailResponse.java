package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundLogEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundMemberEntity;
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
public class RoundDetailResponse {
	private String multigameId;
	private int participantCount;
	private int capacity;
	private Instant createdAt;
	private boolean participated;
	private MyRoundResult myResult;
	private List<AttemptLog> myLog;
	private List<SubjectStat> subjects;

	public static RoundDetailResponse from(MultigameRoundEntity round, List<Object[]> aggregateRows,
			MultigameRoundMemberEntity myRecord, List<MultigameRoundLogEntity> myLogs) {
		return RoundDetailResponse.builder()
				.multigameId(round.getStartTime())
				.participantCount(round.getParticipantCount())
				.capacity(round.getCapacity())
				.createdAt(round.getCreatedAt())
				.participated(myRecord != null)
				.myResult(myRecord != null ? MyRoundResult.from(myRecord) : null)
				.myLog(myLogs.stream().map(AttemptLog::from).toList())
				.subjects(buildSubjectStats(round.getCapacity(), aggregateRows))
				.build();
	}

	private static List<SubjectStat> buildSubjectStats(int capacity, List<Object[]> aggregateRows) {
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
			double competitionRate = capacity > 0
					? Math.round(applied[subjectId] * 10.0 / capacity) / 10.0
					: 0;
			subjects.add(SubjectStat.builder()
					.subjectId(subjectId)
					.applied(applied[subjectId])
					.succeeded(succeeded[subjectId])
					.competitionRate(competitionRate)
					.build());
		}
		return subjects;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MyRoundResult {
		private int subjectId;
		private String status;
		private Instant createdAt;

		public static MyRoundResult from(MultigameRoundMemberEntity member) {
			return MyRoundResult.builder()
					.subjectId(member.getSubjectId())
					.status(member.getStatus())
					.createdAt(member.getCreatedAt())
					.build();
		}
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AttemptLog {
		private String status;
		private long seq;
		private int limit;
		private Instant attemptedAt;

		public static AttemptLog from(MultigameRoundLogEntity log) {
			return AttemptLog.builder()
					.status(log.getAttemptStatus())
					.seq(log.getAttemptSeq())
					.limit(log.getCurrentLimit())
					.attemptedAt(log.getAttemptedAt())
					.build();
		}
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
