package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundLogEntity;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyRoundLogResponse {
	private String multigameId;
	private List<AttemptLog> logs;

	public static MyRoundLogResponse from(String startTime, List<MultigameRoundLogEntity> logs) {
		return MyRoundLogResponse.builder()
				.multigameId(startTime)
				.logs(logs.stream().map(AttemptLog::from).toList())
				.build();
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
}
