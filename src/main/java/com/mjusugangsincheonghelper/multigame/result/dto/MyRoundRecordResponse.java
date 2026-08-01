package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundMemberEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyRoundRecordResponse {
	private String multigameId;
	private int subjectId;
	private String status;
	private Instant createdAt;

	public static MyRoundRecordResponse from(MultigameRoundMemberEntity member) {
		return MyRoundRecordResponse.builder()
				.multigameId(member.getStartTime())
				.subjectId(member.getSubjectId())
				.status(member.getStatus())
				.createdAt(member.getCreatedAt())
				.build();
	}
}
