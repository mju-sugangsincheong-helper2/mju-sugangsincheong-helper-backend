package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.singlegame.dto.cache.QuantileBand;
import org.springframework.stereotype.Component;

/**
 * 분위수 → 백분위순위 근사의 유일한 진입점.
 *
 * <p>모집단 원천({@code QuantileBand} 7점)을 받아 내 기록이 몇 %보다 빠른지
 * 구간별 선형보간으로 근사한다. {@code detail} 성적표와 {@code feedback} 판정은
 * 반드시 이 컴포넌트를 거쳐야 하며, 각자 보간식을 복제하면 안 된다.</p>
 */
@Component
public class SingleGameQuantileInterpolator {

	/** 분위수 고정 순서에 대응하는 백분위순위 */
	private static final double[] RANKS = {5.0, 10.0, 30.0, 50.0, 70.0, 90.0, 95.0};

	/**
	 * 관측 지연의 백분위순위를 근사한다.
	 *
	 * @param durationMs 관측 지연 (ms)
	 * @param band 모집단 분위수묶음 (null·빈 밴드 가능)
	 * @return 0~100. 작을수록 빠름. 모집단 없으면 0
	 */
	public double estimatePercentileRank(int durationMs, QuantileBand band) {
		if (band == null || band.isEmpty()) {
			return 0;
		}
		int[] edges = {band.getQ05(), band.getQ10(), band.getQ30(), band.getQ50(),
				band.getQ70(), band.getQ90(), band.getQ95()};

		if (edges[0] <= 0) {
			return 0;
		}
		if (durationMs <= edges[0]) {
			return Math.max(0, (double) durationMs / edges[0] * RANKS[0]);
		}
		for (int i = 1; i < edges.length; i++) {
			if (durationMs <= edges[i]) {
				int lower = edges[i - 1];
				int upper = edges[i];
				if (upper <= lower) {
					return RANKS[i];
				}
				double fraction = (double) (durationMs - lower) / (upper - lower);
				return RANKS[i - 1] + fraction * (RANKS[i] - RANKS[i - 1]);
			}
		}
		int q95 = edges[edges.length - 1];
		return RANKS[RANKS.length - 1] + Math.min(5.0, (double) (durationMs - q95) / q95 * 5.0);
	}

	/**
	 * 백분위순위를 등급으로 변환한다. 분위수 컷과 일치시킨다.
	 * S(≤5) / A(≤30) / B(&lt;70) / C(&lt;95) / D(그 외).
	 */
	public String assignGrade(double percentileRank) {
		if (percentileRank <= 5) {
			return "S";
		}
		if (percentileRank <= 30) {
			return "A";
		}
		if (percentileRank < 70) {
			return "B";
		}
		if (percentileRank < 95) {
			return "C";
		}
		return "D";
	}

	/** 소수 첫째 자리 반올림 (응답 표기 규칙) */
	public double roundToOneDecimal(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
