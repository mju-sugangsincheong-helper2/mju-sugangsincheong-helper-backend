package com.mjusugangsincheonghelper.multigame.dashboard.service;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultRepository;
import com.mjusugangsincheonghelper.multigame.dashboard.dto.DashboardResponse;
import com.mjusugangsincheonghelper.multigame.dashboard.dto.DashboardResponse.MyRecentResult;
import com.mjusugangsincheonghelper.multigame.dashboard.dto.DashboardResponse.OverallStats;
import com.mjusugangsincheonghelper.multigame.dashboard.dto.DashboardResponse.TodayGame;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MultigameDashboardService {

	private static final int MY_RECENT_RESULTS_LIMIT = 5;

	private final MultigameResultRepository resultRepository;
	private final MultigameResultDetailRepository resultDetailRepository;

	public DashboardResponse getDashboard(Long memberId) {
		List<TodayGame> todayGames = getTodayGames();
		List<MyRecentResult> myRecentResults = getMyRecentResults(memberId);
		OverallStats overallStats = getOverallStats();

		return DashboardResponse.builder()
				.todayGames(todayGames)
				.myRecentResults(myRecentResults)
				.overallStats(overallStats)
				.build();
	}

	private List<TodayGame> getTodayGames() {
		return resultRepository.findAllByOrderByStartTimeAsc().stream()
				.map(result -> TodayGame.builder()
						.multigameId(result.getStartTime())
						.participantCount(result.getParticipantCount())
						.capacity(result.getCapacity())
						.finalizedAt(result.getFinalizedAt())
						.build())
				.toList();
	}

	private List<MyRecentResult> getMyRecentResults(Long memberId) {
		List<MultigameResultDetailEntity> recentDetails = resultDetailRepository
				.findByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(0, MY_RECENT_RESULTS_LIMIT))
				.getContent();

		return recentDetails.stream()
				.map(detail -> MyRecentResult.builder()
						.multigameId(detail.getStartTime())
						.subjectId(detail.getSubjectId())
						.status(detail.getStatus())
						.finalizedAt(resultRepository.findById(detail.getStartTime())
								.map(MultigameResultEntity::getFinalizedAt)
								.orElse(null))
						.build())
				.toList();
	}

	private OverallStats getOverallStats() {
		long totalGames = resultRepository.countTotalGames();
		long totalParticipants = resultRepository.countTotalParticipants();
		double averageParticipants = resultRepository.calculateAverageParticipants();

		return OverallStats.builder()
				.totalGames(totalGames)
				.totalParticipants(totalParticipants)
				.averageParticipants(Math.round(averageParticipants * 100.0) / 100.0)
				.build();
	}
}
