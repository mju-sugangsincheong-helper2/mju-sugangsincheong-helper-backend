package com.mjusugangsincheonghelper.multigame.my.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyHistoryResponse {

	private String multigameId;
	private int subjectId;
	private String status;
	private int participantCount;
	private Instant finalizedAt;

	public static MyHistoryResponse of(MultigameResultDetailEntity detail, MultigameResultEntity result) {
		return MyHistoryResponse.builder()
				.multigameId(detail.getStartTime())
				.subjectId(detail.getSubjectId())
				.status(detail.getStatus())
				.participantCount(result.getParticipantCount())
				.finalizedAt(result.getFinalizedAt())
				.build();
	}
}
