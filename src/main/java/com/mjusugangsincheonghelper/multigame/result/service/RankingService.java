package com.mjusugangsincheonghelper.multigame.result.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundMemberRepository;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse.MyDepartment;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse.ParticipationEntry;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse.PerformanceEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

	private final MultigameRoundMemberRepository roundMemberRepository;
	private final MemberRepository memberRepository;

	/** 한 라운드에서 취득할 수 있는 최대 과목 수 (과목 1~6). */
	private static final int SUBJECT_COUNT_PER_ROUND = 6;

	public MultigameRankingResponse rankings(long memberId) {
		Map<String, List<Double>> ratesByDepartment = collectSuccessRatesByDepartment();

		List<DepartmentRankingCalculator.Participation> participation = DepartmentRankingCalculator.participation(ratesByDepartment);
		List<DepartmentRankingCalculator.Performance> performance = DepartmentRankingCalculator.performance(ratesByDepartment);

		return MultigameRankingResponse.builder()
				.participation(participation.stream()
						.map(item -> ParticipationEntry.builder()
								.department(item.department())
								.participantCount(item.participantCount())
								.build())
						.toList())
				.performance(performance.stream()
						.map(item -> PerformanceEntry.builder()
								.department(item.department())
								.top70AvgSuccessRate(item.top70AvgSuccessRate())
								.participantCount(item.participantCount())
								.build())
						.toList())
				.myDepartment(myDepartment(memberId, participation, performance))
				.build();
	}

	private Map<String, List<Double>> collectSuccessRatesByDepartment() {
		Map<String, List<Double>> ratesByDepartment = new HashMap<>();
		for (Object[] row : roundMemberRepository.aggregateByMemberDepartment()) {
			String department = (String) row[1];
			long successCount = ((Number) row[2]).longValue();
			long roundsPlayed = ((Number) row[3]).longValue();
			// 성공률 = 성공 과목 수 / (참여 라운드 수 × 6). 라운드당 최대 6과목 중 몇 개를
			// 쟁취했는지(평균 취득률)를 고정 분모로 측정한다. 신청하지 않은 라운드는 집계 대상이
			// 아니므로(멤버 레코드 없음), 진입만 한 유저는 성적 집계에서 제외된다.
			double successRate = roundsPlayed > 0 ? successCount * 100.0 / (roundsPlayed * SUBJECT_COUNT_PER_ROUND) : 0.0;
			ratesByDepartment.computeIfAbsent(department, key -> new ArrayList<>()).add(successRate);
		}
		return ratesByDepartment;
	}

	private MyDepartment myDepartment(long memberId, List<DepartmentRankingCalculator.Participation> participation,
			List<DepartmentRankingCalculator.Performance> performance) {
		String department = memberRepository.findById(memberId)
				.map(Member::getDepartment)
				.filter(value -> value != null && !value.isBlank())
				.orElse(null);
		if (department == null) {
			return null;
		}
		int participationIndex = indexOfDepartment(participation, department, DepartmentRankingCalculator.Participation::department);
		int performanceIndex = indexOfDepartment(performance, department, DepartmentRankingCalculator.Performance::department);
		if (participationIndex < 0 || performanceIndex < 0) {
			return null;
		}
		return MyDepartment.builder()
				.department(department)
				.participationRank(participationIndex + 1)
				.performanceRank(performanceIndex + 1)
				.participantCount(participation.get(participationIndex).participantCount())
				.top70AvgSuccessRate(performance.get(performanceIndex).top70AvgSuccessRate())
				.build();
	}

	private <T> int indexOfDepartment(List<T> items, String department, Function<T, String> departmentExtractor) {
		for (int index = 0; index < items.size(); index++) {
			if (departmentExtractor.apply(items.get(index)).equals(department)) {
				return index;
			}
		}
		return -1;
	}
}
