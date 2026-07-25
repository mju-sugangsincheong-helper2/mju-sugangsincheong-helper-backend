package com.mjusugangsincheonghelper.multigame.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.multigame.stats.dto.MyStatsResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameMyStatsService 테스트")
class MultigameMyStatsServiceTest {

	@Mock
	private MultigameResultDetailRepository resultDetailRepository;

	@InjectMocks
	private MultigameMyStatsService myStatsService;

	@Test
	@DisplayName("내 참여 통계 요약을 조회한다")
	void getMyStats_returns_stats_summary() {
		// given
		Long memberId = 1L;

		given(resultDetailRepository.countByMemberId(memberId)).willReturn(10L);
		given(resultDetailRepository.countByMemberIdAndStatus(memberId, "SUCCESS")).willReturn(8L);
		given(resultDetailRepository.countByMemberIdAndStatus(memberId, "FAIL_SOLDOUT")).willReturn(1L);
		given(resultDetailRepository.countByMemberIdAndStatus(memberId, "FAIL_DUPLICATE")).willReturn(1L);

		List<Object[]> subjectBreakdown = List.of(
				new Object[]{3, 5L, 4L},
				new Object[]{1, 3L, 2L},
				new Object[]{2, 2L, 2L}
		);
		given(resultDetailRepository.findSubjectBreakdownByMemberId(memberId))
				.willReturn(subjectBreakdown);

		// when
		MyStatsResponse stats = myStatsService.getMyStats(memberId);

		// then
		assertThat(stats.getTotalGames()).isEqualTo(10L);
		assertThat(stats.getSuccessCount()).isEqualTo(8L);
		assertThat(stats.getFailSoldoutCount()).isEqualTo(1L);
		assertThat(stats.getFailDuplicateCount()).isEqualTo(1L);
		assertThat(stats.getSuccessRate()).isEqualTo(80.0);
		assertThat(stats.getMostRequestedSubject()).isEqualTo(3);
		assertThat(stats.getSubjectBreakdown()).hasSize(3);
		assertThat(stats.getSubjectBreakdown().get(0).getSubjectId()).isEqualTo(3);
		assertThat(stats.getSubjectBreakdown().get(0).getCount()).isEqualTo(5L);
		assertThat(stats.getSubjectBreakdown().get(0).getSuccess()).isEqualTo(4L);
	}

	@Test
	@DisplayName("참여 기록이 없을 때 빈 통계를 반환한다")
	void getMyStats_returns_empty_stats_when_no_history() {
		// given
		Long memberId = 1L;

		given(resultDetailRepository.countByMemberId(memberId)).willReturn(0L);
		given(resultDetailRepository.countByMemberIdAndStatus(memberId, "SUCCESS")).willReturn(0L);
		given(resultDetailRepository.countByMemberIdAndStatus(memberId, "FAIL_SOLDOUT")).willReturn(0L);
		given(resultDetailRepository.countByMemberIdAndStatus(memberId, "FAIL_DUPLICATE")).willReturn(0L);
		given(resultDetailRepository.findSubjectBreakdownByMemberId(memberId))
				.willReturn(List.of());

		// when
		MyStatsResponse stats = myStatsService.getMyStats(memberId);

		// then
		assertThat(stats.getTotalGames()).isEqualTo(0L);
		assertThat(stats.getSuccessCount()).isEqualTo(0L);
		assertThat(stats.getSuccessRate()).isEqualTo(0.0);
		assertThat(stats.getMostRequestedSubject()).isNull();
		assertThat(stats.getSubjectBreakdown()).isEmpty();
	}
}
