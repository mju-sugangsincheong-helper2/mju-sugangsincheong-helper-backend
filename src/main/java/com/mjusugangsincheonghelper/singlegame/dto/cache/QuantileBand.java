package com.mjusugangsincheonghelper.singlegame.dto.cache;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단일 지표의 분위수 7점 묶음. 모집단 분포의 유일한 원천.
 *
 * <p>순서 고정: {@code [Q05, Q10, Q30, Q50, Q70, Q90, Q95]} (단위 ms).
 * {@code timeline} 차트는 이 값을 그대로 그리고,
 * {@code detail/feedback}은 {@code SingleGameQuantileInterpolator}로
 * 백분위순위(percentileRank)를 근사한다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuantileBand implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 5분위수 (ms). 등급 S(≤5) 경계 판정에 필요 */
	private int q05;

	/** 10분위수 (ms) */
	private int q10;

	/** 30분위수 (ms) */
	private int q30;

	/** 50분위수 = 중앙값 (ms) */
	private int q50;

	/** 70분위수 (ms) */
	private int q70;

	/** 90분위수 (ms) */
	private int q90;

	/** 95분위수 (ms). 등급 D(≥95) 경계 판정에 필요 */
	private int q95;

	public static QuantileBand empty() {
		return QuantileBand.builder()
				.q05(0).q10(0).q30(0).q50(0).q70(0).q90(0).q95(0)
				.build();
	}

	public boolean isEmpty() {
		return q10 <= 0 && q50 <= 0 && q90 <= 0;
	}
}
