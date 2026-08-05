package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundMemberEntity;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내 참여 기록 목록의 라운드 단위 행.
 * 게임 시작 시각(multigameId = T), 참여자 수(participantCount),
 * 자신이 6개 과목 중 몇 개를 성공했는지(successCount)만 노출한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyRoundRecordResponse {
	private String multigameId;
	private int participantCount;
	private int successCount;

	public static MyRoundRecordResponse from(MultigameRoundEntity round, List<MultigameRoundMemberEntity> myRecords) {
		return MyRoundRecordResponse.builder()
				.multigameId(round.getStartTime())
				.participantCount(round.getParticipantCount())
				.successCount((int) myRecords.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count())
				.build();
	}
}
