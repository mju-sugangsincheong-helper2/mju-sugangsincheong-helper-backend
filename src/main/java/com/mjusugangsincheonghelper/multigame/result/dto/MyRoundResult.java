package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundMemberEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 라운드에서 유저가 취득/마감 처리된 과목별 최종 결과.
 * 한 라운드에 과목 수만큼(최대 6개) 존재할 수 있다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyRoundResult {
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
