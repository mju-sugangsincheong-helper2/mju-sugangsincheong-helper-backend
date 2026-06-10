package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SingleGameService 단위 테스트")
class SingleGameServiceTest {

	@Mock
	private SingleGameRepository singleGameRepository;

	@Mock
	private SingleGameDetailRepository singleGameDetailRepository;

	@Mock
	private MemberRepository memberRepository;

	@InjectMocks
	private SingleGameService singleGameService;

	@Captor
	private ArgumentCaptor<SingleGameEntity> gameCaptor;

	@Captor
	private ArgumentCaptor<List<SingleGameDetailEntity>> detailsCaptor;

	@Nested
	@DisplayName("saveGame 메서드는")
	class Describe_saveGame {

		@Test
		@DisplayName("유효한 요청을 받으면 게임을 저장하고 응답을 반환한다")
		void it_saves_game_and_returns_response() throws Exception {
			SingleGameDetailRequest detail = SingleGameDetailRequest.builder()
					.sequence(1).tClickCourse(1000).tClickYes(500).tClickOk(300)
					.build();
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).isCompleted(true).tEnterMain(2000)
					.details(List.of(detail))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);
			given(singleGameRepository.save(any())).willAnswer(invocation -> {
				SingleGameEntity entity = invocation.getArgument(0);
				Field idField = SingleGameEntity.class.getDeclaredField("id");
				idField.setAccessible(true);
				idField.set(entity, 100L);
				return entity;
			});

			SingleGameSaveResponse response = singleGameService.saveGame(1L, request);

			assertThat(response.getGameId()).isEqualTo(100L);
			assertThat(response.getMessage()).isNotNull();

			verify(singleGameRepository).save(gameCaptor.capture());
			SingleGameEntity saved = gameCaptor.getValue();
			assertThat(saved.getTTotal()).isEqualTo(3800); // 2000 + 1000 + 500 + 300
			assertThat(saved.getTotalCourses()).isEqualTo(6);
			assertThat(saved.isCompleted()).isTrue();

			verify(singleGameDetailRepository).saveAll(detailsCaptor.capture());
			assertThat(detailsCaptor.getValue()).hasSize(1);
			assertThat(detailsCaptor.getValue().get(0).getGameId()).isEqualTo(100L);
		}

		@Test
		@DisplayName("존재하지 않는 회원이면 예외를 던진다")
		void it_throws_when_member_not_found() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).isCompleted(true).tEnterMain(2000)
					.details(List.of())
					.build();

			given(memberRepository.existsById(999L)).willReturn(false);

			assertThatThrownBy(() -> singleGameService.saveGame(999L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("허용되지 않은 totalCourses이면 예외를 던진다")
		void it_throws_when_total_courses_invalid() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(4).isCompleted(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}
	}

	@Nested
	@DisplayName("getRankings 메서드는")
	class Describe_getRankings {

		@Test
		@DisplayName("GLOBAL 범위로 랭킹을 반환한다")
		void it_returns_global_rankings() {
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 6, 5000, 2000, Instant.now()};
			given(singleGameRepository.findRankingRaw(6)).willReturn(List.<Object[]>of(row));

			RankingResponse response = singleGameService.getRankings(6, "GLOBAL", null);

			assertThat(response.getRankings()).hasSize(1);
			assertThat(response.getRankings().get(0).getName()).isEqualTo("홍길동");
			assertThat(response.getScope()).isEqualTo("GLOBAL");
		}

		@Test
		@DisplayName("DEPARTMENT 범위로 랭킹을 반환한다")
		void it_returns_department_rankings() {
			Member member = Member.builder()
					.role(Member.Role.MEMBER)
					.name("홍길동")
					.department("컴퓨터공학과")
					.build();
			given(memberRepository.findById(1L)).willReturn(Optional.of(member));

			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 6, 5000, 2000, Instant.now()};
			given(singleGameRepository.findDeptRankingRaw(6, "컴퓨터공학과")).willReturn(List.<Object[]>of(row));

			RankingResponse response = singleGameService.getRankings(6, "DEPARTMENT", 1L);

			assertThat(response.getRankings()).hasSize(1);
			assertThat(response.getScope()).isEqualTo("DEPARTMENT");
		}

		@Test
		@DisplayName("totalCourses가 3 이상이면 서브 랭킹도 포함한다")
		void it_includes_sub_rankings_when_total_courses_ge_3() {
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 6, 5000, 2000, Instant.now()};
			given(singleGameRepository.findRankingRaw(6)).willReturn(List.<Object[]>of(row));

			Object[] firstClick = {1L, "홍길동", 800};
			given(singleGameRepository.findFirstClickRaw(6)).willReturn(List.<Object[]>of(firstClick));

			RankingResponse response = singleGameService.getRankings(6, "GLOBAL", null);

			assertThat(response.getSubRankings()).isNotNull();
			assertThat(response.getSubRankings().getEnterMainTop3()).hasSize(1);
			assertThat(response.getSubRankings().getFirstClickTop3()).hasSize(1);
		}

		@Test
		@DisplayName("서브 랭킹의 rank는 1,2,3으로 할당된다")
		void it_assigns_correct_ranks_in_sub_rankings() {
			Instant now = Instant.now();
			Object[] row1 = {1L, 1L, "1등", "학과A", 6, 3000, 100, now};
			Object[] row2 = {2L, 2L, "2등", "학과B", 6, 4000, 200, now};
			Object[] row3 = {3L, 3L, "3등", "학과C", 6, 5000, 300, now};
			given(singleGameRepository.findRankingRaw(6)).willReturn(List.<Object[]>of(row1, row2, row3));

			Object[] fc1 = {1L, "1등", 200};
			Object[] fc2 = {2L, "2등", 300};
			Object[] fc3 = {3L, "3등", 400};
			given(singleGameRepository.findFirstClickRaw(6)).willReturn(List.<Object[]>of(fc1, fc2, fc3));

			RankingResponse response = singleGameService.getRankings(6, "GLOBAL", null);

			assertThat(response.getSubRankings().getEnterMainTop3()).hasSize(3);
			assertThat(response.getSubRankings().getEnterMainTop3().get(0).getRank()).isEqualTo(1);
			assertThat(response.getSubRankings().getEnterMainTop3().get(1).getRank()).isEqualTo(2);
			assertThat(response.getSubRankings().getEnterMainTop3().get(2).getRank()).isEqualTo(3);

			assertThat(response.getSubRankings().getFirstClickTop3()).hasSize(3);
			assertThat(response.getSubRankings().getFirstClickTop3().get(0).getRank()).isEqualTo(1);
			assertThat(response.getSubRankings().getFirstClickTop3().get(1).getRank()).isEqualTo(2);
			assertThat(response.getSubRankings().getFirstClickTop3().get(2).getRank()).isEqualTo(3);
		}

		@Test
		@DisplayName("랭킹 목록은 상위 20개만 반환한다")
		void it_limits_rankings_to_top_20() {
			Instant now = Instant.now();
			List<Object[]> rows = new java.util.ArrayList<>();
			for (int i = 1; i <= 25; i++) {
				rows.add(new Object[]{(long) i, (long) i, "유저" + i, "학과", 6, 1000 + i * 100, 100, now});
			}
			given(singleGameRepository.findRankingRaw(6)).willReturn(rows);

			RankingResponse response = singleGameService.getRankings(6, "GLOBAL", null);

			assertThat(response.getRankings()).hasSize(20);
			assertThat(response.getRankings().get(0).getRank()).isEqualTo(1);
			assertThat(response.getRankings().get(19).getRank()).isEqualTo(20);
		}

		@Test
		@DisplayName("totalCourses가 3 미만이면 서브 랭킹은 null이다")
		void it_excludes_sub_rankings_when_total_courses_lt_3() {
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 2, 5000, 2000, Instant.now()};
			given(singleGameRepository.findRankingRaw(2)).willReturn(List.<Object[]>of(row));

			RankingResponse response = singleGameService.getRankings(2, "GLOBAL", null);

			assertThat(response.getSubRankings()).isNull();
		}
	}

	@Nested
	@DisplayName("getMyRecords 메서드는")
	class Describe_getMyRecords {

		@Test
		@DisplayName("회원의 게임 기록 목록을 페이지로 반환한다")
		void it_returns_my_records_page() throws Exception {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(5000).tEnterMain(2000)
					.isCompleted(true).totalCourses(6)
					.build();
			Field idField = SingleGameEntity.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(game, 100L);

			given(singleGameRepository.findByMemberIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
					.willReturn(new PageImpl<>(List.of(game)));
			given(singleGameRepository.countByTotalCoursesAndIsCompletedTrue(6)).willReturn(50L);
			given(singleGameRepository.findGameIdsWithBetterOrEqualTTotal(6, 5000))
					.willReturn(List.of(1L, 2L, 3L));
			given(memberRepository.findById(1L)).willReturn(Optional.empty());

			Page<MyRecordResponse> records = singleGameService.getMyRecords(1L, 0, 10);

			assertThat(records.getContent()).hasSize(1);
			assertThat(records.getContent().get(0).getTotalCourses()).isEqualTo(6);
			assertThat(records.getContent().get(0).getTTotal()).isEqualTo(5000);
			assertThat(records.getContent().get(0).getRanking().getGlobal().getRank()).isEqualTo(3);
		}
	}

	@Nested
	@DisplayName("getAnalysis 메서드는")
	class Describe_getAnalysis {

		@Test
		@DisplayName("유효한 게임 ID에 대해 분석 결과를 반환한다")
		void it_returns_analysis() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(12000).tEnterMain(2000)
					.isCompleted(true).totalCourses(6)
					.build();

			SingleGameDetailEntity detail = SingleGameDetailEntity.builder()
					.gameId(1L).sequence(1).tClickCourse(3000).tClickYes(1000).tClickOk(500)
					.build();

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L))
					.willReturn(List.of(detail));
			given(singleGameRepository.countByTotalCoursesAndIsCompletedTrue(6)).willReturn(100L);
			given(singleGameRepository.findGameIdsWithBetterOrEqualTTotal(6, 12000))
					.willReturn(List.of(1L, 2L, 3L, 4L, 5L));
			given(singleGameRepository.findSequencePercentileStats(6)).willReturn(List.of());
			given(singleGameRepository.findAllDetailsByTotalCourses(6)).willReturn(List.of());
			given(singleGameRepository.findGameIdsWithBetterOrEqualEnterMain(6, 2000))
					.willReturn(List.of(1L, 2L, 3L));

			AnalysisResponse response = singleGameService.getAnalysis(1L);

			assertThat(response.getGameId()).isEqualTo(1L);
			assertThat(response.getSummary()).isNotNull();
			assertThat(response.getSummary().getFeedbackCode()).isNotNull();
			assertThat(response.getDetails()).hasSize(1);
			assertThat(response.getDetails().get(0).getMine().getClickCourse()).isEqualTo(3000);
		}

		@Test
		@DisplayName("존재하지 않는 게임 ID면 예외를 던진다")
		void it_throws_when_game_not_found() {
			given(singleGameRepository.findById(999L)).willReturn(Optional.empty());

			assertThatThrownBy(() -> singleGameService.getAnalysis(999L))
					.isInstanceOf(BaseException.class);
		}
	}
}
