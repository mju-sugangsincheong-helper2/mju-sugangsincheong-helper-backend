package com.mjusugangsincheonghelper.multigame.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.multigame.stats.dto.DepartmentParticipationStatsResponse;
import com.mjusugangsincheonghelper.multigame.stats.dto.DepartmentSuccessRateStatsResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameDepartmentStatsService 테스트")
class MultigameDepartmentStatsServiceTest {

	@Mock
	private MultigameResultDetailRepository resultDetailRepository;

	@Mock
	private MemberRepository memberRepository;

	@InjectMocks
	private MultigameDepartmentStatsService departmentStatsService;

	@Test
	@DisplayName("학과별 참여 횟수 순위를 조회한다")
	void getParticipationStats_returns_rankings() {
		// given
		Long memberId = 1L;
		Member member = Member.builder()
				.department("컴퓨터공학과")
				.build();

		List<Object[]> statsRaw = List.of(
				new Object[]{"컴퓨터공학과", 100L, 80L},
				new Object[]{"전자공학과", 80L, 60L},
				new Object[]{"소프트웨어학과", 60L, 50L}
		);

		given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
		given(resultDetailRepository.findDepartmentParticipationStats()).willReturn(statsRaw);

		// when
		DepartmentParticipationStatsResponse stats = departmentStatsService.getParticipationStats(memberId);

		// then
		assertThat(stats.getRankings()).hasSize(3);
		assertThat(stats.getRankings().get(0).getRank()).isEqualTo(1);
		assertThat(stats.getRankings().get(0).getDepartment()).isEqualTo("컴퓨터공학과");
		assertThat(stats.getRankings().get(0).getParticipationCount()).isEqualTo(100L);
		assertThat(stats.getRankings().get(1).getRank()).isEqualTo(2);
		assertThat(stats.getRankings().get(1).getDepartment()).isEqualTo("전자공학과");

		assertThat(stats.getMyDepartment()).isNotNull();
		assertThat(stats.getMyDepartment().getDepartment()).isEqualTo("컴퓨터공학과");
		assertThat(stats.getMyDepartment().getParticipationCount()).isEqualTo(100L);
		assertThat(stats.getMyDepartment().getRank()).isEqualTo(1);
	}

	@Test
	@DisplayName("학과별 성공률 순위를 조회한다")
	void getSuccessRateStats_returns_rankings() {
		// given
		Long memberId = 1L;
		Member member = Member.builder()
				.department("전자공학과")
				.build();

		List<Object[]> statsRaw = List.of(
				new Object[]{"컴퓨터공학과", 100L, 85L},
				new Object[]{"전자공학과", 80L, 70L},
				new Object[]{"소프트웨어학과", 60L, 45L}
		);

		given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
		given(resultDetailRepository.findDepartmentSuccessRateStats()).willReturn(statsRaw);

		// when
		DepartmentSuccessRateStatsResponse stats = departmentStatsService.getSuccessRateStats(memberId);

		// then
		assertThat(stats.getRankings()).hasSize(3);
		assertThat(stats.getRankings().get(0).getRank()).isEqualTo(1);
		assertThat(stats.getRankings().get(0).getDepartment()).isEqualTo("컴퓨터공학과");
		assertThat(stats.getRankings().get(0).getSuccessRate()).isEqualTo(85.0);
		assertThat(stats.getRankings().get(1).getRank()).isEqualTo(2);
		assertThat(stats.getRankings().get(1).getDepartment()).isEqualTo("전자공학과");
		assertThat(stats.getRankings().get(1).getSuccessRate()).isEqualTo(87.5);

		assertThat(stats.getMyDepartment()).isNotNull();
		assertThat(stats.getMyDepartment().getDepartment()).isEqualTo("전자공학과");
		assertThat(stats.getMyDepartment().getSuccessRate()).isEqualTo(87.5);
		assertThat(stats.getMyDepartment().getRank()).isEqualTo(2);
	}

	@Test
	@DisplayName("내 학과가 순위 목록에 없으면 null을 반환한다")
	void getParticipationStats_returns_null_myDepartment_when_not_in_rankings() {
		// given
		Long memberId = 1L;
		Member member = Member.builder()
				.department("기계공학과")
				.build();

		List<Object[]> statsRaw = List.of(
				new Object[]{"컴퓨터공학과", 100L, 80L},
				new Object[]{"전자공학과", 80L, 60L}
		);

		given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
		given(resultDetailRepository.findDepartmentParticipationStats()).willReturn(statsRaw);

		// when
		DepartmentParticipationStatsResponse stats = departmentStatsService.getParticipationStats(memberId);

		// then
		assertThat(stats.getRankings()).hasSize(2);
		assertThat(stats.getMyDepartment()).isNotNull();
		assertThat(stats.getMyDepartment().getDepartment()).isEqualTo("기계공학과");
		assertThat(stats.getMyDepartment().getParticipationCount()).isEqualTo(0L);
		assertThat(stats.getMyDepartment().getRank()).isEqualTo(3);
	}
}
