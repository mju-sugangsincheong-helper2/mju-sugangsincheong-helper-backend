package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.singlegame.config.SingleGameProperties;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse;
import com.mjusugangsincheonghelper.singlegame.dto.DepartmentsResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import com.mjusugangsincheonghelper.singlegame.dto.cache.FeedbackQuantileTable;
import com.mjusugangsincheonghelper.singlegame.dto.cache.QuantileBand;
import com.mjusugangsincheonghelper.singlegame.dto.cache.SequenceQuantileTable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SingleGameRankingService 단위 테스트")
class SingleGameRankingServiceTest {

	@Mock
	private SingleGameRepository singleGameRepository;

	@Mock
	private MemberRepository memberRepository;

	private SingleGameRankingService rankingService;

	@BeforeEach
	void setUp() {
		rankingService = new SingleGameRankingService(singleGameRepository, memberRepository, new SingleGameRankingSnapshotCache(singleGameRepository));
	}

	@Nested
	@DisplayName("getRankings 메서드는")
	class Describe_getRankings {

		@Test
		@DisplayName("GLOBAL 범위로 랭킹을 반환한다")
		void it_returns_global_rankings() {
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 6, 5000, 2000, System.currentTimeMillis()};
			given(singleGameRepository.findRankingRaw(6, 20)).willReturn(List.<Object[]>of(row));

			RankingResponse response = rankingService.getRankings(6, "GLOBAL", null, null);

			assertThat(response.getRankings()).hasSize(1);
			assertThat(response.getRankings().get(0).getName()).isEqualTo("**동");
			assertThat(response.getScope()).isEqualTo("GLOBAL");
		}

		@Test
		@DisplayName("DEPARTMENT 범위로 본인 학과 랭킹을 반환한다")
		void it_returns_department_rankings() {
			Member member = Member.builder()
					.role(Member.Role.MEMBER)
					.name("홍길동")
					.department("컴퓨터공학과")
					.build();
			given(memberRepository.findById(1L)).willReturn(Optional.of(member));

			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 6, 5000, 2000, System.currentTimeMillis()};
			given(singleGameRepository.findDeptRankingRaw(6, "컴퓨터공학과", 20)).willReturn(List.<Object[]>of(row));

			RankingResponse response = rankingService.getRankings(6, "DEPARTMENT", null, 1L);

			assertThat(response.getRankings()).hasSize(1);
			assertThat(response.getScope()).isEqualTo("DEPARTMENT");
		}

		@Test
		@DisplayName("DEPARTMENT 범위에서 department 파라미터가 있으면 해당 학과 랭킹을 반환한다")
		void it_returns_department_rankings_with_department_param() {
			Member member = Member.builder()
					.role(Member.Role.MEMBER)
					.name("홍길동")
					.department("컴퓨터공학과")
					.build();
			given(memberRepository.findById(1L)).willReturn(Optional.of(member));

			Object[] row = {1L, 1L, "홍길동", "전자공학과", 6, 5000, 2000, System.currentTimeMillis()};
			given(singleGameRepository.findDeptRankingRaw(6, "전자공학과", 20)).willReturn(List.<Object[]>of(row));

			RankingResponse response = rankingService.getRankings(6, "DEPARTMENT", "전자공학과", 1L);

			assertThat(response.getRankings()).hasSize(1);
			assertThat(response.getScope()).isEqualTo("DEPARTMENT");
		}

		@Test
		@DisplayName("동점자는 같은 순위를 받고 다음 순위는 건너뛴다")
		void it_assigns_tied_ranks() {
			long now = System.currentTimeMillis();
			Object[] row1 = {1L, 1L, "1등", "학과A", 6, 3000, 100, now};
			Object[] row2 = {2L, 2L, "2등", "학과B", 6, 4000, 200, now};
			Object[] row3 = {3L, 3L, "3등", "학과C", 6, 4000, 300, now};
			Object[] row4 = {4L, 4L, "4등", "학과D", 6, 5000, 400, now};
			given(singleGameRepository.findRankingRaw(6, 20))
					.willReturn(List.<Object[]>of(row1, row2, row3, row4));

			RankingResponse response = rankingService.getRankings(6, "GLOBAL", null, null);

			assertThat(response.getRankings()).hasSize(4);
			assertThat(response.getRankings().get(0).getRank()).isEqualTo(1);
			assertThat(response.getRankings().get(1).getRank()).isEqualTo(2);
			assertThat(response.getRankings().get(2).getRank()).isEqualTo(2);
			assertThat(response.getRankings().get(3).getRank()).isEqualTo(4);
		}

		@Test
		@DisplayName("GLOBAL 범위에서 내 대표판 기준 myRank를 반환한다")
		void it_returns_global_my_rank() {
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 6, 5000, 2000, System.currentTimeMillis()};
			given(singleGameRepository.findRankingRaw(6, 20)).willReturn(List.<Object[]>of(row));
			Object[] best = {10L, 3000, 150};
			given(singleGameRepository.findMyBestGame(1L, 6)).willReturn(List.<Object[]>of(best));
			given(singleGameRepository.countBetterPersons(6, 3000)).willReturn(2L);

			RankingResponse response = rankingService.getRankings(6, "GLOBAL", null, 1L);

			assertThat(response.getMyRank()).isNotNull();
			assertThat(response.getMyRank().getRank()).isEqualTo(3);
			assertThat(response.getMyRank().getGameId()).isEqualTo(10L);
			assertThat(response.getMyRank().getTTotal()).isEqualTo(3000);
		}

		@Test
		@DisplayName("DEPARTMENT 범위에서 해당 학과 내 myRank를 반환한다")
		void it_returns_department_my_rank() {
			Member member = Member.builder()
					.role(Member.Role.MEMBER)
					.name("홍길동")
					.department("컴퓨터공학과")
					.build();
			given(memberRepository.findById(1L)).willReturn(Optional.of(member));
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 6, 5000, 2000, System.currentTimeMillis()};
			given(singleGameRepository.findDeptRankingRaw(6, "컴퓨터공학과", 20))
					.willReturn(List.<Object[]>of(row));
			Object[] best = {10L, 3000, 150};
			given(singleGameRepository.findMyBestGame(1L, 6)).willReturn(List.<Object[]>of(best));
			given(singleGameRepository.countBetterDeptPersons(6, "컴퓨터공학과", 3000)).willReturn(1L);

			RankingResponse response = rankingService.getRankings(6, "DEPARTMENT", null, 1L);

			assertThat(response.getMyRank()).isNotNull();
			assertThat(response.getMyRank().getRank()).isEqualTo(2);
		}

		@Test
		@DisplayName("학과 없는 요청자가 학과를 지정하면 400 에러를 던진다")
		void it_throws_when_guest_specifies_department() {
			Member guest = Member.builder()
					.role(Member.Role.GUEST)
					.name("게스트")
					.build();
			given(memberRepository.findById(1L)).willReturn(Optional.of(guest));

			assertThatThrownBy(() -> rankingService.getRankings(6, "DEPARTMENT", "경영학과", 1L))
					.isInstanceOf(BaseException.class)
					.extracting(e -> ((BaseException) e).getErrorCode())
					.isEqualTo(ErrorCode.SINGLEGAME_INVALID_RANK_DEPARTMENT);
		}

		@Test
		@DisplayName("학과 없는 요청자가 학과를 생략해도 400 에러를 던진다")
		void it_throws_when_guest_requests_department_scope() {
			Member guest = Member.builder()
					.role(Member.Role.GUEST)
					.name("게스트")
					.build();
			given(memberRepository.findById(1L)).willReturn(Optional.of(guest));

			assertThatThrownBy(() -> rankingService.getRankings(6, "DEPARTMENT", null, 1L))
					.isInstanceOf(BaseException.class)
					.extracting(e -> ((BaseException) e).getErrorCode())
					.isEqualTo(ErrorCode.SINGLEGAME_INVALID_RANK_DEPARTMENT);
		}

		@Test
		@DisplayName("완료판이 없으면 myRank는 null이다")
		void it_returns_null_my_rank_when_no_games() {
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 6, 5000, 2000, System.currentTimeMillis()};
			given(singleGameRepository.findRankingRaw(6, 20)).willReturn(List.<Object[]>of(row));
			given(singleGameRepository.findMyBestGame(1L, 6)).willReturn(List.of());

			RankingResponse response = rankingService.getRankings(6, "GLOBAL", null, 1L);

			assertThat(response.getMyRank()).isNull();
		}

		@Test
		@DisplayName("DB에서 받은 상위 20개를 그대로 매핑한다")
		void it_maps_top_20_from_db() {
			long now = System.currentTimeMillis();
			List<Object[]> rows = new java.util.ArrayList<>();
			for (int i = 1; i <= 20; i++) {
				rows.add(new Object[]{(long) i, (long) i, "유저" + i, "학과", 6, 1000 + i * 100, 100, now});
			}
			given(singleGameRepository.findRankingRaw(6, 20)).willReturn(rows);

			RankingResponse response = rankingService.getRankings(6, "GLOBAL", null, null);

			assertThat(response.getRankings()).hasSize(20);
			assertThat(response.getRankings().get(0).getRank()).isEqualTo(1);
			assertThat(response.getRankings().get(19).getRank()).isEqualTo(20);
		}

		@Test
		@DisplayName("totalCourses가 3 미만이어도 랭킹을 반환한다")
		void it_returns_rankings_when_total_courses_lt_3() {
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 2, 5000, 2000, System.currentTimeMillis()};
			given(singleGameRepository.findRankingRaw(2, 20)).willReturn(List.<Object[]>of(row));

			RankingResponse response = rankingService.getRankings(2, "GLOBAL", null, null);

			assertThat(response.getRankings()).hasSize(1);
		}
	@Nested
	@DisplayName("getDepartments 메서드는")
	class Describe_getDepartments {

		@Test
		@DisplayName("모든 학과 목록을 반환한다")
		void it_returns_all_departments() {
			given(singleGameRepository.findDistinctDepartments())
					.willReturn(List.of("간호학과", "경영학과", "건축학과", "컴퓨터공학과"));

			DepartmentsResponse response = rankingService.getDepartments();

			assertThat(response.getDepartments()).hasSize(4);
			assertThat(response.getDepartments()).containsExactly("간호학과", "경영학과", "건축학과", "컴퓨터공학과");
		}

		@Test
		@DisplayName("학과가 없으면 빈 목록을 반환한다")
		void it_returns_empty_list_when_no_departments() {
			given(singleGameRepository.findDistinctDepartments()).willReturn(List.of());

			DepartmentsResponse response = rankingService.getDepartments();

			assertThat(response.getDepartments()).isEmpty();
		}
	}
	}
}
