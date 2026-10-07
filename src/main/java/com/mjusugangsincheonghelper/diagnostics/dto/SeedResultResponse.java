package com.mjusugangsincheonghelper.diagnostics.dto;

import java.util.List;

/**
 * dev 전용 싱글게임 시드 결과. 쌓은 다음 쿼리 속도 테스트를 진행하기 위한 전제 데이터를 보고한다.
 */
public record SeedResultResponse(
		int games,
		int details,
		int totalCourses,
		List<String> departments,
		int memberPoolSize,
		double elapsedMs
) {
}
