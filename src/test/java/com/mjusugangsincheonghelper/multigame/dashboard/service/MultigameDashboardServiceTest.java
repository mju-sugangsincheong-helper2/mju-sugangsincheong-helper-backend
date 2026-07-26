package com.mjusugangsincheonghelper.multigame.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultRepository;
import com.mjusugangsincheonghelper.multigame.dashboard.dto.DashboardResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameDashboardService 테스트")
class MultigameDashboardServiceTest {

	@Mock
	private MultigameResultRepository resultRepository;

	@Mock
	private MultigameResultDetailRepository resultDetailRepository;

	@InjectMocks
	private MultigameDashboardService dashboardService;

	@Test
	@DisplayName("대시보드를 조회한다")
	void getDashboard_returns_dashboard() {
		// given
		Long memberId = 1L;

		MultigameResultEntity todayGame = MultigameResultEntity.builder()
				.startTime("20260725120000")
				.participantCount(100)
				.capacity(50)
				.finalizedAt(Instant.now())
				.build();

		MultigameResultDetailEntity myDetail = MultigameResultDetailEntity.builder()
				.startTime("20260725120000")
				.memberId(memberId)
				.subjectId(3)
				.status("SUCCESS")
				.build();

		Page<MultigameResultDetailEntity> detailPage = new PageImpl<>(List.of(myDetail));

		given(resultRepository.findAllByOrderByStartTimeDesc()).willReturn(List.of(todayGame));
		given(resultDetailRepository.findByMemberIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
				.willReturn(detailPage);
		given(resultRepository.findById("20260725120000")).willReturn(Optional.of(todayGame));
		given(resultRepository.countTotalGames()).willReturn(100L);
		given(resultRepository.countTotalParticipants()).willReturn(5000L);
		given(resultRepository.calculateAverageParticipants()).willReturn(50.0);

		// when
		DashboardResponse dashboard = dashboardService.getDashboard(memberId);

		// then
		assertThat(dashboard.getRecentGames()).hasSize(1);
		assertThat(dashboard.getRecentGames().get(0).getMultigameId()).isEqualTo("20260725120000");
		assertThat(dashboard.getRecentGames().get(0).getParticipantCount()).isEqualTo(100);

		assertThat(dashboard.getMyRecentResults()).hasSize(1);
		assertThat(dashboard.getMyRecentResults().get(0).getSubjectId()).isEqualTo(3);
		assertThat(dashboard.getMyRecentResults().get(0).getStatus()).isEqualTo("SUCCESS");

		assertThat(dashboard.getOverallStats().getTotalGames()).isEqualTo(100L);
		assertThat(dashboard.getOverallStats().getTotalParticipants()).isEqualTo(5000L);
		assertThat(dashboard.getOverallStats().getAverageParticipants()).isEqualTo(50.0);
	}

	@Test
	@DisplayName("게임이 없으면 빈 목록을 반환한다")
	void getDashboard_returns_empty_recentGames_when_no_games() {
		// given
		Long memberId = 1L;

		given(resultRepository.findAllByOrderByStartTimeDesc()).willReturn(List.of());
		given(resultDetailRepository.findByMemberIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
				.willReturn(Page.empty());
		given(resultRepository.countTotalGames()).willReturn(0L);
		given(resultRepository.countTotalParticipants()).willReturn(0L);
		given(resultRepository.calculateAverageParticipants()).willReturn(0.0);

		// when
		DashboardResponse dashboard = dashboardService.getDashboard(memberId);

		// then
		assertThat(dashboard.getRecentGames()).isEmpty();
		assertThat(dashboard.getMyRecentResults()).isEmpty();
		assertThat(dashboard.getOverallStats().getTotalGames()).isEqualTo(0L);
	}
}
