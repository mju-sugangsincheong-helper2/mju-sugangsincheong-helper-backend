package com.mjusugangsincheonghelper.singlegame.dto.cache;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 피드백 판정용 모집단 분위수표. totalCourses 단위로 캐시된다.
 *
 * <p>기존 {@code FeedbackBands(int[4])}의 7점 확장판.
 * 판별 축약값(조준평균·연타평균·첫구간합·기복표준편차)의 분위수만 들고 있어
 * 게임별 원시 리스트를 캐시하지 않으므로 페이로드가 상수 크기다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackQuantileTable implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 조준평균(avg t_click_course) 분포 */
	private QuantileBand aimAvg;

	/** 연타평균(avg t_click_yes + avg t_click_ok) 분포 */
	private QuantileBand burstAvg;

	/** 첫구간합(첫 시도 TOTAL) 분포 */
	private QuantileBand firstTotal;

	/** 페이스 기복(판내 TOTAL 표준편차) 분포. N&lt;3판은 모집단에서 제외 */
	private QuantileBand paceStd;

	/** 모집단 판 수 */
	private long sampleSize;

	public static FeedbackQuantileTable empty() {
		return FeedbackQuantileTable.builder()
				.aimAvg(QuantileBand.empty())
				.burstAvg(QuantileBand.empty())
				.firstTotal(QuantileBand.empty())
				.paceStd(QuantileBand.empty())
				.sampleSize(0)
				.build();
	}
}
