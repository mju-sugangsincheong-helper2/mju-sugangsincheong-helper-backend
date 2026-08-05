package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 종료된 라운드 목록의 요약 행.
 * 게임 시작 시각(multigameId = T)과 참여자 수(participantCount)만 노출한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundSummaryResponse {
	private String multigameId;
	private int participantCount;

	public static RoundSummaryResponse from(MultigameRoundEntity round) {
		return RoundSummaryResponse.builder()
				.multigameId(round.getStartTime())
				.participantCount(round.getParticipantCount())
				.build();
	}
}
