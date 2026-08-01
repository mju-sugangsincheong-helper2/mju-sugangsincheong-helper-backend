package com.mjusugangsincheonghelper.multigame.result.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundMemberRepository;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RankingService 단위 테스트")
class RankingServiceTest {

	@Mock
	private MultigameRoundMemberRepository roundMemberRepository;

	@Mock
	private MemberRepository memberRepository;

	private RankingService service;

	@BeforeEach
	void setUp() {
		service = new RankingService(roundMemberRepository, memberRepository);
	}

	private void aggregateRows(Object[]... rows) {
		given(roundMemberRepository.aggregateByMemberDepartment()).willReturn(List.of(rows));
	}

	private Member member(String department) {
		return Member.builder()
				.role(Member.Role.MEMBER)
				.department(department)
				.name("홍길동")
				.build();
	}

	// ---------------------------------------------------------------------
	// rankings
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("rankings 메서드는")
	class Describe_rankings {

		@Test
		@DisplayName("학과별 참가 수와 상위 70% 성공률 순위를 반환한다")
		void it_returns_department_rankings() {
			// {memberId, department, successCount, roundsPlayed} → 성공률 = success / (rounds × 6)
			aggregateRows(
					new Object[]{1L, "컴퓨터공학과", 3L, 1L}, // 3/(1×6) = 50.0
					new Object[]{2L, "컴퓨터공학과", 3L, 5L}, // 3/(5×6) = 10.0
					new Object[]{3L, "전자공학과", 3L, 2L});  // 3/(2×6) = 25.0
			given(memberRepository.findById(1L)).willReturn(Optional.of(member("컴퓨터공학과")));

			MultigameRankingResponse response = service.rankings(1L);

			// 참가 수 내림차순: 컴퓨터공학과(2) > 전자공학과(1)
			assertThat(response.getParticipation()).extracting(MultigameRankingResponse.ParticipationEntry::getDepartment)
					.containsExactly("컴퓨터공학과", "전자공학과");
			assertThat(response.getParticipation()).extracting(MultigameRankingResponse.ParticipationEntry::getParticipantCount)
					.containsExactly(2, 1);

			// 성적: 컴퓨터공학과(50,10)→30.0 / 전자공학과(25)→25.0
			assertThat(response.getPerformance()).extracting(MultigameRankingResponse.PerformanceEntry::getDepartment)
					.containsExactly("컴퓨터공학과", "전자공학과");
			assertThat(response.getPerformance()).extracting(MultigameRankingResponse.PerformanceEntry::getTop70AvgSuccessRate)
					.containsExactly(30.0, 25.0);

			// 내 학과: 참가 1위, 성적 1위
			assertThat(response.getMyDepartment()).isNotNull();
			assertThat(response.getMyDepartment().getDepartment()).isEqualTo("컴퓨터공학과");
			assertThat(response.getMyDepartment().getParticipationRank()).isEqualTo(1);
			assertThat(response.getMyDepartment().getPerformanceRank()).isEqualTo(1);
			assertThat(response.getMyDepartment().getParticipantCount()).isEqualTo(2);
			assertThat(response.getMyDepartment().getTop70AvgSuccessRate()).isEqualTo(30.0);
		}

		@Test
		@DisplayName("성공률이 다른 학과는 성공률 내림차순으로 정렬된다")
		void it_sorts_by_success_rate() {
			aggregateRows(
					new Object[]{1L, "경영학과", 6L, 1L},   // 6/(1×6) = 100.0
					new Object[]{2L, "전자공학과", 6L, 5L}); // 6/(5×6) = 20.0
			given(memberRepository.findById(1L)).willReturn(Optional.of(member("경영학과")));

			MultigameRankingResponse response = service.rankings(1L);

			assertThat(response.getPerformance()).extracting(MultigameRankingResponse.PerformanceEntry::getDepartment)
					.containsExactly("경영학과", "전자공학과");
		}

		@Test
		@DisplayName("회원이 존재하지 않으면 myDepartment는 null이다")
		void it_returns_null_my_department_when_member_absent() {
			aggregateRows(new Object[]{1L, "컴퓨터공학과", 8L, 10L});
			given(memberRepository.findById(1L)).willReturn(Optional.empty());

			MultigameRankingResponse response = service.rankings(1L);

			assertThat(response.getMyDepartment()).isNull();
		}

		@Test
		@DisplayName("회원의 학과가 비어 있으면 myDepartment는 null이다")
		void it_returns_null_my_department_when_blank() {
			aggregateRows(new Object[]{1L, "컴퓨터공학과", 8L, 10L});
			given(memberRepository.findById(1L)).willReturn(Optional.of(member(" ")));

			MultigameRankingResponse response = service.rankings(1L);

			assertThat(response.getMyDepartment()).isNull();
		}

		@Test
		@DisplayName("회원의 학과가 집계에 없으면 myDepartment는 null이다")
		void it_returns_null_my_department_when_department_not_ranked() {
			aggregateRows(new Object[]{1L, "컴퓨터공학과", 8L, 10L});
			given(memberRepository.findById(1L)).willReturn(Optional.of(member("간호학과")));

			MultigameRankingResponse response = service.rankings(1L);

			assertThat(response.getMyDepartment()).isNull();
		}

		@Test
		@DisplayName("기록이 없는 유저는 성공률 0으로 참가자 수에 포함된다")
		void it_counts_failed_only_users_as_participants() {
			// roundsPlayed = 0 → 성공률 0 (0으로 나누기 방지)
			aggregateRows(new Object[]{1L, "경영학과", 0L, 0L});
			given(memberRepository.findById(anyLong())).willReturn(Optional.of(member("경영학과")));

			MultigameRankingResponse response = service.rankings(1L);

			assertThat(response.getParticipation()).extracting(MultigameRankingResponse.ParticipationEntry::getParticipantCount)
					.containsExactly(1);
			assertThat(response.getPerformance()).extracting(MultigameRankingResponse.PerformanceEntry::getTop70AvgSuccessRate)
					.containsExactly(0.0);
		}
	}
}
