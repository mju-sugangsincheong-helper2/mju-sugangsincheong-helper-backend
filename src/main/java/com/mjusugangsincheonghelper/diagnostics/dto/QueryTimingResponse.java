package com.mjusugangsincheonghelper.diagnostics.dto;

import java.util.Map;

/**
 * dev 전용 쿼리 실행 시간 측정 응답. 모든 진단 API가 공유하는 표준 포맷.
 *
 * <p>단일 학과 1회 측정한다. 100만 규모 실측에서 20개 학과가 2.3~2.6초 박스권에 평평했고
 * 학과 간 편차도 10% 이내라(균등 분산 시드) 1개 학과가 대표값으로 충분하기 때문이다.</p>
 */
public record QueryTimingResponse(
		String query,
		Map<String, Object> params,
		double ms,
		int rowCount,
		String note
) {
}
