package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
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
public class MultigameResultResponse {

	private String multigameId;
	private int participantCount;
	private int capacity;
	private Instant finalizedAt;
	private List<MultigameResultDetailResponse> details;

	public static MultigameResultResponse of(MultigameResultEntity entity, List<MultigameResultDetailResponse> details) {
		return MultigameResultResponse.builder()
				.multigameId(entity.getStartTime())
				.participantCount(entity.getParticipantCount())
				.capacity(entity.getCapacity())
				.finalizedAt(entity.getFinalizedAt())
				.details(details)
				.build();
	}
}
