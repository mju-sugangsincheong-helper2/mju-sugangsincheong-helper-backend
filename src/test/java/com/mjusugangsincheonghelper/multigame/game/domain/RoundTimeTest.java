package com.mjusugangsincheonghelper.multigame.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("RoundTime T 계산 단위 테스트")
class RoundTimeTest {

	// ---------------------------------------------------------------------
	// target(): now 기준 타겟 라운드 T 산출
	// ---------------------------------------------------------------------

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("targetCases")
	@DisplayName("now의 분값에 따라 직전/다음 10분 마크로 T를 산출한다")
	void target(LocalDateTime now, String expected) {
		assertThat(RoundTime.target(now)).isEqualTo(expected);
	}

	static Stream<Arguments> targetCases() {
		// M <= 1 (:00 ~ :01) → 직전 10분 마크
		// M > 1 (:02 ~ :09) → 다음 10분 마크
		return Stream.of(
				// 정각 → 자기 자신
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 0, 0), "20260801120000"),
				// 진행 직후 1분 (:01) → 직전 마크
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 0, 45), "20260801120000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 1, 0), "20260801120000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 1, 59), "20260801120000"),
				// 대기 구간 (:02 ~ :09) → 다음 마크
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 2, 0), "20260801121000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 9, 54), "20260801121000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 9, 59), "20260801121000"),
				// 10분 마크 진입
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 10, 0), "20260801121000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 10, 30), "20260801121000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 11, 30), "20260801121000"),
				// 정각 직전 1초
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 49, 59), "20260801125000"));
	}

	// ---------------------------------------------------------------------
	// currentMark(): 현재 시각이 속한 10분 마크 (T)
	// ---------------------------------------------------------------------

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("currentMarkCases")
	@DisplayName("현재 시각이 속한 10분 마크를 반환한다")
	void currentMark(LocalDateTime now, String expected) {
		assertThat(RoundTime.currentMark(now)).isEqualTo(expected);
	}

	static Stream<Arguments> currentMarkCases() {
		return Stream.of(
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 0, 0), "20260801120000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 0, 29), "20260801120000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 0, 30), "20260801120000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 9, 59), "20260801120000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 10, 0), "20260801121000"),
				Arguments.of(LocalDateTime.of(2026, 8, 1, 12, 19, 59), "20260801121000"));
	}

	// ---------------------------------------------------------------------
	// parse(): yyyyMMddHHmmss ↔ LocalDateTime 변환
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("14자리 문자열을 LocalDateTime으로 파싱하고 재포맷할 수 있다")
	void parseRoundTrips() {
		LocalDateTime parsed = RoundTime.parse("20260801120000");
		assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 8, 1, 12, 0, 0));
		assertThat(RoundTime.target(parsed)).isEqualTo("20260801120000");
	}
}
