package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundSummaryResponse {
	private String multigameId;
	private int participantCount;
	private int capacity;
	private Instant createdAt;

	public static RoundSummaryResponse from(MultigameRoundEntity round) {
		return RoundSummaryResponse.builder()
				.multigameId(round.getStartTime())
				.participantCount(round.getParticipantCount())
				.capacity(round.getCapacity())
				.createdAt(round.getCreatedAt())
				.build();
	}
}
