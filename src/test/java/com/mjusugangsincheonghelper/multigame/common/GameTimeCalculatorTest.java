package com.mjusugangsincheonghelper.multigame.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("GameTimeCalculator 테스트")
class GameTimeCalculatorTest {

	@Nested
	@DisplayName("computeNextT는")
	class Describe_computeNextT {

		@ParameterizedTest
		@CsvSource({
				"2026, 6, 30, 12, 0, 0, 20260630121000",
				"2026, 6, 30, 12, 5, 0, 20260630121000",
				"2026, 6, 30, 12, 9, 59, 20260630121000",
				"2026, 6, 30, 12, 10, 0, 20260630122000",
				"2026, 6, 30, 12, 15, 30, 20260630122000",
				"2026, 6, 30, 12, 50, 0, 20260630130000",
				"2026, 6, 30, 23, 55, 0, 20260701000000",
		})
		@DisplayName("현재 시각의 다음 10분 마크를 반환한다")
		void it_returns_next_ten_mark(int year, int month, int day, int hour, int minute, int second, String expected) {
			LocalDateTime now = LocalDateTime.of(year, month, day, hour, minute, second);
			String result = GameTimeCalculator.computeNextT(now);
			assertThat(result).isEqualTo(expected);
		}
	}

	@Nested
	@DisplayName("computeCurrentT는")
	class Describe_computeCurrentT {

		@ParameterizedTest
		@CsvSource({
				"2026, 6, 30, 12, 0, 0, 20260630120000",
				"2026, 6, 30, 12, 5, 0, 20260630120000",
				"2026, 6, 30, 12, 9, 59, 20260630120000",
				"2026, 6, 30, 12, 10, 0, 20260630121000",
				"2026, 6, 30, 12, 15, 30, 20260630121000",
				"2026, 6, 30, 12, 50, 0, 20260630125000",
				"2026, 6, 30, 12, 59, 59, 20260630125000",
		})
		@DisplayName("현재 시각이 속한 10분 마크를 반환한다")
		void it_returns_current_ten_mark(int year, int month, int day, int hour, int minute, int second, String expected) {
			LocalDateTime now = LocalDateTime.of(year, month, day, hour, minute, second);
			String result = GameTimeCalculator.computeCurrentT(now);
			assertThat(result).isEqualTo(expected);
		}
	}

	@Nested
	@DisplayName("parseT는")
	class Describe_parseT {

		@Test
		@DisplayName("14자리 문자열을 LocalDateTime으로 파싱한다")
		void it_parses_string_to_localDateTime() {
			String t = "20260630120000";
			LocalDateTime result = GameTimeCalculator.parseT(t);
			assertThat(result).isEqualTo(LocalDateTime.of(2026, 6, 30, 12, 0, 0));
		}
	}

	@Nested
	@DisplayName("formatT는")
	class Describe_formatT {

		@Test
		@DisplayName("LocalDateTime을 14자리 문자열로 포맷한다")
		void it_formats_localDateTime_to_string() {
			LocalDateTime dateTime = LocalDateTime.of(2026, 6, 30, 12, 0, 0);
			String result = GameTimeCalculator.formatT(dateTime);
			assertThat(result).isEqualTo("20260630120000");
		}
	}
}
