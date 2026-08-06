package com.mjusugangsincheonghelper.multigame.result.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DepartmentRankingCalculatorTest {

	@Test
	void participationIsSortedByParticipantCountDescending() {
		Map<String, List<Double>> rates = Map.of(
				"컴퓨터공학과", List.of(100.0, 50.0, 0.0),
				"전자공학과", List.of(100.0),
				"경영학과", List.of(80.0, 20.0));

		List<DepartmentRankingCalculator.Participation> result = DepartmentRankingCalculator.calculateParticipation(rates);

		assertThat(result).extracting(DepartmentRankingCalculator.Participation::department)
				.containsExactly("컴퓨터공학과", "경영학과", "전자공학과");
		assertThat(result).extracting(DepartmentRankingCalculator.Participation::participantCount)
				.containsExactly(3, 2, 1);
	}

	@Test
	void performanceAveragesTheTopSeventyPercentBySuccessRate() {
		List<Double> rates = List.of(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);

		List<DepartmentRankingCalculator.Performance> result =
				DepartmentRankingCalculator.calculatePerformance(Map.of("컴퓨터공학과", rates));

		// 상위 70% = ceil(10 * 0.7) = 7명 → (100+90+80+70+60+50+40) / 7 = 70.0
		assertThat(result.getFirst().top70AvgSuccessRate()).isEqualTo(70.0);
	}

	@Test
	void smallDepartmentAlwaysIncludesAtLeastOneUser() {
		List<DepartmentRankingCalculator.Performance> result =
				DepartmentRankingCalculator.calculatePerformance(Map.of("컴퓨터공학과", List.of(50.0)));

		assertThat(result.getFirst().top70AvgSuccessRate()).isEqualTo(50.0);
	}

	@Test
	void performanceTieIsBrokenByLargerParticipantCount() {
		Map<String, List<Double>> rates = Map.of(
				"경영학과", List.of(100.0, 0.0),
				"전자공학과", List.of(50.0));

		List<DepartmentRankingCalculator.Performance> result = DepartmentRankingCalculator.calculatePerformance(rates);

		// 둘 다 평균 50.0 → 참가자 수가 많은 경영학과가 먼저
		assertThat(result).extracting(DepartmentRankingCalculator.Performance::department)
				.containsExactly("경영학과", "전자공학과");
	}

	@Test
	void failedOnlyUsersAreCountedAsParticipantsWithZeroRate() {
		Map<String, List<Double>> rates = Map.of(
				"컴퓨터공학과", List.of(0.0, 0.0, 100.0),
				"전자공학과", List.of(0.0));

		List<DepartmentRankingCalculator.Participation> participation = DepartmentRankingCalculator.calculateParticipation(rates);
		List<DepartmentRankingCalculator.Performance> performance = DepartmentRankingCalculator.calculatePerformance(rates);

		assertThat(participation).extracting(DepartmentRankingCalculator.Participation::participantCount)
				.containsExactly(3, 1);
		// 컴퓨터공학과 상위 70% = ceil(3 * 0.7) = 3명 → (100 + 0 + 0) / 3 = 33.3
		assertThat(performance.getFirst().top70AvgSuccessRate()).isEqualTo(33.3);
	}
}
