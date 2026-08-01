package com.mjusugangsincheonghelper.multigame.result.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내 참여 기록 목록의 라운드 단위 행.
 * 한 라운드에서 유저는 과목별로 각각 최종 상태를 가지므로(최대 6개), 결과는 results 배열로 묶어 제공한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyRoundRecordResponse {
	private String multigameId;
	private Instant createdAt;
	private List<MyRoundResult> results;

	public static MyRoundRecordResponse from(String startTime, List<MyRoundResult> results) {
		Instant createdAt = results.isEmpty() ? null : results.getFirst().getCreatedAt();
		return MyRoundRecordResponse.builder()
				.multigameId(startTime)
				.createdAt(createdAt)
				.results(results)
				.build();
	}
}
