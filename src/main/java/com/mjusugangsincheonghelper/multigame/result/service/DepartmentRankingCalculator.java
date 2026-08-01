package com.mjusugangsincheonghelper.multigame.result.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 학과 랭킹 산정 로직 (순수 계산).
 *
 * <p>집계 규칙:
 * <ul>
 *   <li>참가 수 순위: 학과별 참가자(유니크 member) 수 내림차순.</li>
 *   <li>성적 순위: 학과별로 유저를 성공률(성공 과목 수 / (참여 라운드 수 × 6)) 내림차순 정렬한 뒤,
 *       상위 70% 인원(최소 1명)의 평균 성공률 내림차순.</li>
 * </ul>
 * <p>한 라운드에서 유저는 과목별로 각각 성공할 수 있으므로(최대 6개), 성공률은 고정 분모 6
 * (라운드당 최대 취득 과목 수) 기준의 라운드당 평균 취득률로 계산됩니다. 즉 6과목 중 몇 개를
 * 쟁취했는지를 라운드 수만큼 평균한 값입니다. 신청 기록이 없는 라운드/유저는 집계에 포함되지 않습니다.</p>
 */
public final class DepartmentRankingCalculator {

	private static final double TOP_RATIO = 0.7;

	private DepartmentRankingCalculator() {
	}

	public record Participation(String department, int participantCount) {
	}

	public record Performance(String department, double top70AvgSuccessRate, int participantCount) {
	}

	public static List<Participation> participation(Map<String, List<Double>> successRatesByDepartment) {
		return successRatesByDepartment.entrySet().stream()
				.map(entry -> new Participation(entry.getKey(), entry.getValue().size()))
				.sorted(Comparator.comparingInt(Participation::participantCount).reversed()
						.thenComparing(Participation::department))
				.toList();
	}

	public static List<Performance> performance(Map<String, List<Double>> successRatesByDepartment) {
		return successRatesByDepartment.entrySet().stream()
				.map(entry -> {
					List<Double> rates = entry.getValue().stream()
							.sorted(Comparator.reverseOrder())
							.toList();
					int topCount = Math.max(1, (int) Math.ceil(rates.size() * TOP_RATIO));
					double average = rates.subList(0, topCount).stream()
							.mapToDouble(Double::doubleValue)
							.average()
							.orElse(0);
					return new Performance(entry.getKey(), round1(average), entry.getValue().size());
				})
				.sorted(Comparator.comparingDouble(Performance::top70AvgSuccessRate).reversed()
						.thenComparing(Performance::participantCount, Comparator.reverseOrder())
						.thenComparing(Performance::department))
				.toList();
	}

	private static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
