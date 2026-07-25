package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultigameResultDetailResponse {

	private Long memberId;
	private int subjectId;
	private String status;

	public static MultigameResultDetailResponse from(MultigameResultDetailEntity entity) {
		return MultigameResultDetailResponse.builder()
				.memberId(entity.getMemberId())
				.subjectId(entity.getSubjectId())
				.status(entity.getStatus())
				.build();
	}
}
