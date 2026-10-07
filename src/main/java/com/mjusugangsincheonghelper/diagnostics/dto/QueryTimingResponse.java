package com.mjusugangsincheonghelper.diagnostics.dto;

import java.util.List;
import java.util.Map;

/**
 * dev 전용 쿼리 실행 시간 측정 응답. 모든 진단 API가 공유하는 표준 포맷.
 */
public record QueryTimingResponse(
		String query,
		Map<String, Object> params,
		int warmupRuns,
		List<Double> runsMs,
		double minMs,
		double avgMs,
		double maxMs,
		int rowCount,
		List<DeptTiming> departments,
		String note
) {
	/** 학과별 측정 내역 */
	public record DeptTiming(
			String department,
			List<Double> runsMs,
			double avgMs,
			int rowCount
	) {
	}
}
