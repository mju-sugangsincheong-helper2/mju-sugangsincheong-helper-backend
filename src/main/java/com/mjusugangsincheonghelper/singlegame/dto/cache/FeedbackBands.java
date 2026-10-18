package com.mjusugangsincheonghelper.singlegame.dto.cache;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 피드백 판정용 모집단 분포표. totalCourses 단위로 캐시된다.
 *
 * <p>판별 축약값(조준평균·연타평균·첫구간·기복)의 분위수만 들고 있다.
 * 게임별 원시 리스트를 캐시하지 않으므로 페이로드가 상수 크기다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackBands implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 조준평균 분포 int[4] = p10/p30/p50/p70 */
	private int[] aim;

	/** 연타평균 분포 int[4] */
	private int[] burst;

	/** 첫구간 분포 int[4] */
	private int[] first;

	/** 페이스 기복 분포 int[4]. N<3판은 모집단에서 제외된다 */
	private int[] pace;

	/** 모집단 판 수 */
	private long n;

	public static FeedbackBands empty() {
		return FeedbackBands.builder()
				.aim(new int[4])
				.burst(new int[4])
				.first(new int[4])
				.pace(new int[4])
				.n(0)
				.build();
	}
}
