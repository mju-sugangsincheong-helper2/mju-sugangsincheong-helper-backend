package com.mjusugangsincheonghelper.multigame.stats.service;

import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.multigame.stats.dto.MyStatsResponse;
import com.mjusugangsincheonghelper.multigame.stats.dto.MyStatsResponse.SubjectBreakdown;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MultigameMyStatsService {

	private final MultigameResultDetailRepository resultDetailRepository;

	public MyStatsResponse getMyStats(Long memberId) {
		long totalGames = resultDetailRepository.countByMemberId(memberId);
		long successCount = resultDetailRepository.countByMemberIdAndStatus(memberId, "SUCCESS");
		long failSoldoutCount = resultDetailRepository.countByMemberIdAndStatus(memberId, "FAIL_SOLDOUT");
		long failDuplicateCount = resultDetailRepository.countByMemberIdAndStatus(memberId, "FAIL_DUPLICATE");

		double successRate = totalGames > 0 ? (double) successCount / totalGames * 100 : 0.0;

		List<Object[]> subjectBreakdownRaw = resultDetailRepository.findSubjectBreakdownByMemberId(memberId);
		List<SubjectBreakdown> subjectBreakdown = subjectBreakdownRaw.stream()
				.map(row -> SubjectBreakdown.builder()
						.subjectId(((Number) row[0]).intValue())
						.count(((Number) row[1]).longValue())
						.success(((Number) row[2]).longValue())
						.build())
				.toList();

		Integer mostRequestedSubject = subjectBreakdown.isEmpty() ? null : subjectBreakdown.get(0).getSubjectId();

		return MyStatsResponse.builder()
				.totalGames(totalGames)
				.successCount(successCount)
				.failSoldoutCount(failSoldoutCount)
				.failDuplicateCount(failDuplicateCount)
				.successRate(successRate)
				.mostRequestedSubject(mostRequestedSubject)
				.subjectBreakdown(subjectBreakdown)
				.build();
	}
}
