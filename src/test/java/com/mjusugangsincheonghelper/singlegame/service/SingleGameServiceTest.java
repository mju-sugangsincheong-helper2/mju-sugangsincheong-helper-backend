package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.singlegame.config.SingleGameProperties;
import com.mjusugangsincheonghelper.singlegame.dto.DepartmentsResponse;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import com.mjusugangsincheonghelper.singlegame.dto.cache.FeedbackBands;
import com.mjusugangsincheonghelper.singlegame.dto.cache.PopulationTable;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SingleGameService 단위 테스트")
class SingleGameServiceTest {

	@Mock
	private SingleGameRepository singleGameRepository;

	@Mock
	private SingleGameDetailRepository singleGameDetailRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private SingleGameDataMergeService singleGameDataMergeService;

	@Mock
	private SingleGameStatsService singleGameStatsService;

	private SingleGameService singleGameService;

	@Captor
	private ArgumentCaptor<SingleGameEntity> gameCaptor;

	@Captor
	private ArgumentCaptor<List<SingleGameDetailEntity>> detailsCaptor;

	@BeforeEach
	void setUp() {
		SingleGameRankingCacheService rankingCacheService = new SingleGameRankingCacheService(singleGameRepository);
		singleGameService = new SingleGameService(
				singleGameRepository, singleGameDetailRepository, memberRepository, new SingleGameFeedbackEngine(), new SingleGameProperties(), singleGameDataMergeService, singleGameStatsService, rankingCacheService);
	}

	@Nested
	@DisplayName("saveGame 메서드는")
	class Describe_saveGame {

		@Test
		@DisplayName("유효한 요청을 받으면 게임을 저장하고 응답을 반환한다")
		void it_saves_game_and_returns_response() throws Exception {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(1000).tClickYes(500).tClickOk(300).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
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
			assertThat(saved.getTTotal()).isEqualTo(2000 + 6 * 1800);
			assertThat(saved.getTotalCourses()).isEqualTo(6);
			assertThat(saved.isCompleted()).isTrue();

			verify(singleGameDetailRepository).saveAll(detailsCaptor.capture());
			assertThat(detailsCaptor.getValue()).hasSize(6);
			assertThat(detailsCaptor.getValue().get(0).getGameId()).isEqualTo(100L);
		}

		@Test
		@DisplayName("존재하지 않는 회원이면 예외를 던진다")
		void it_throws_when_member_not_found() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
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
					.totalCourses(4).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 0이면 예외를 던진다")
		void it_throws_when_total_courses_is_zero() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(0).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 2이면 예외를 던진다")
		void it_throws_when_total_courses_is_two() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(2).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 5이면 예외를 던진다")
		void it_throws_when_total_courses_is_five() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(5).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 9이면 예외를 던진다")
		void it_throws_when_total_courses_is_nine() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(9).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 음수이면 예외를 던진다")
		void it_throws_when_total_courses_is_negative() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(-5).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 1이면 정상 저장된다")
		void it_saves_when_total_courses_is_one() throws Exception {
			SingleGameDetailRequest detail = SingleGameDetailRequest.builder()
					.sequence(1).tClickCourse(1000).tClickYes(500).tClickOk(300)
					.build();
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(1).completed(true).tEnterMain(2000)
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
		}

		@Test
		@DisplayName("totalCourses가 7이면 정상 저장된다")
		void it_saves_when_total_courses_is_seven() throws Exception {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 7; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(1000).tClickYes(500).tClickOk(300).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(7).completed(true).tEnterMain(2000)
					.details(details)
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
		}

		@Test
		@DisplayName("totalCourses가 8이면 정상 저장된다")
		void it_saves_when_total_courses_is_eight() throws Exception {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 8; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(1000).tClickYes(500).tClickOk(300).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(8).completed(true).tEnterMain(2000)
					.details(details)
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
		}

		@Test
		@DisplayName("completed=true인데 details 개수가 totalCourses와 다르면 예외를 던진다")
		void it_throws_when_completed_but_details_count_mismatch() {
			SingleGameDetailRequest detail1 = SingleGameDetailRequest.builder()
					.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build();
			SingleGameDetailRequest detail2 = SingleGameDetailRequest.builder()
					.sequence(2).tClickCourse(100).tClickYes(100).tClickOk(100).build();
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(List.of(detail1, detail2))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("completed=false인데 details 개수가 totalCourses와 같으면 예외를 던진다")
		void it_throws_when_not_completed_but_details_count_equals_total() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(false).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("completed=false이고 details 개수가 totalCourses보다 적으면 정상 저장된다")
		void it_saves_when_not_completed_and_details_less_than_total() throws Exception {
			SingleGameDetailRequest detail = SingleGameDetailRequest.builder()
					.sequence(1).tClickCourse(1000).tClickYes(500).tClickOk(300)
					.build();
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(false).tEnterMain(2000)
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
		}

		@Test
		@DisplayName("completed=true이고 details 개수가 totalCourses와 같으면 정상 저장된다")
		void it_saves_when_completed_and_details_count_matches() throws Exception {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
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
		}

		@Test
		@DisplayName("tEnterMain이 최소값 미만이면 예외를 던진다")
		void it_throws_when_t_enter_main_below_min() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(0)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tEnterMain이 최대값 초과이면 예외를 던진다")
		void it_throws_when_t_enter_main_above_max() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(65000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickCourse가 최소값 미만이면 예외를 던진다")
		void it_throws_when_t_click_course_below_min() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(0).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickCourse가 최대값 초과이면 예외를 던진다")
		void it_throws_when_t_click_course_above_max() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(65000).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickYes가 최소값 미만이면 예외를 던진다")
		void it_throws_when_t_click_yes_below_min() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(0).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickYes가 최대값 초과이면 예외를 던진다")
		void it_throws_when_t_click_yes_above_max() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(65000).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickOk가 최소값 미만이면 예외를 던진다")
		void it_throws_when_t_click_ok_below_min() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(0).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> singleGameService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickOk가 최대값 초과이면 예외를 던진다")
		void it_throws_when_t_click_ok_above_max() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(65000).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
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
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 6, 5000, 2000, System.currentTimeMillis()};
			given(singleGameRepository.findRankingRaw(6, 20)).willReturn(List.<Object[]>of(row));

			RankingResponse response = singleGameService.getRankings(6, "GLOBAL", null, null);

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

			RankingResponse response = singleGameService.getRankings(6, "DEPARTMENT", null, 1L);

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

			RankingResponse response = singleGameService.getRankings(6, "DEPARTMENT", "전자공학과", 1L);

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

			RankingResponse response = singleGameService.getRankings(6, "GLOBAL", null, null);

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

			RankingResponse response = singleGameService.getRankings(6, "GLOBAL", null, 1L);

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

			RankingResponse response = singleGameService.getRankings(6, "DEPARTMENT", null, 1L);

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

			assertThatThrownBy(() -> singleGameService.getRankings(6, "DEPARTMENT", "경영학과", 1L))
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

			assertThatThrownBy(() -> singleGameService.getRankings(6, "DEPARTMENT", null, 1L))
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

			RankingResponse response = singleGameService.getRankings(6, "GLOBAL", null, 1L);

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

			RankingResponse response = singleGameService.getRankings(6, "GLOBAL", null, null);

			assertThat(response.getRankings()).hasSize(20);
			assertThat(response.getRankings().get(0).getRank()).isEqualTo(1);
			assertThat(response.getRankings().get(19).getRank()).isEqualTo(20);
		}

		@Test
		@DisplayName("totalCourses가 3 미만이어도 랭킹을 반환한다")
		void it_returns_rankings_when_total_courses_lt_3() {
			Object[] row = {1L, 1L, "홍길동", "컴퓨터공학과", 2, 5000, 2000, System.currentTimeMillis()};
			given(singleGameRepository.findRankingRaw(2, 20)).willReturn(List.<Object[]>of(row));

			RankingResponse response = singleGameService.getRankings(2, "GLOBAL", null, null);

			assertThat(response.getRankings()).hasSize(1);
		}
	}

	@Nested
	@DisplayName("getDepartments 메서드는")
	class Describe_getDepartments {

		@Test
		@DisplayName("모든 학과 목록을 반환한다")
		void it_returns_all_departments() {
			given(singleGameRepository.findDistinctDepartments())
					.willReturn(List.of("간호학과", "경영학과", "건축학과", "컴퓨터공학과"));

			DepartmentsResponse response = singleGameService.getDepartments();

			assertThat(response.getDepartments()).hasSize(4);
			assertThat(response.getDepartments()).containsExactly("간호학과", "경영학과", "건축학과", "컴퓨터공학과");
		}

		@Test
		@DisplayName("학과가 없으면 빈 목록을 반환한다")
		void it_returns_empty_list_when_no_departments() {
			given(singleGameRepository.findDistinctDepartments()).willReturn(List.of());

			DepartmentsResponse response = singleGameService.getDepartments();

			assertThat(response.getDepartments()).isEmpty();
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

			Page<MyRecordResponse> records = singleGameService.getMyRecords(1L, 0, 10);

			assertThat(records.getContent()).hasSize(1);
			assertThat(records.getContent().get(0).getGameId()).isEqualTo(100L);
			assertThat(records.getContent().get(0).getTotalCourses()).isEqualTo(6);
			assertThat(records.getContent().get(0).getTTotal()).isEqualTo(5000);
		}
	}

	@Nested
	@DisplayName("getAnalysis 메서드는")
	class Describe_getAnalysis {

		private PopulationTable emptyPopulation() {
			return PopulationTable.builder()
					.seqBands(Map.of())
					.enterMain(null)
					.build();
		}

		private PopulationTable populationWithEnterMain(int p10, int p30, int p50, int p70) {
			return PopulationTable.builder()
					.seqBands(Map.of())
					.enterMain(new int[]{p10, p30, p50, p70})
					.build();
		}

		private FeedbackBands emptyBands() {
			return FeedbackBands.empty();
		}

		/**
		 * 테스트용 FeedbackBands 생성 헬퍼: raw details로부터 판별 축약값을 모아
		 * 지표별 nearest-rank p10/p30/p50/p70을 계산한다 (findFeedbackBands 대응).
		 */
		private FeedbackBands bandsFromRawDetails(List<Object[]> allDetails) {
			Map<Long, List<Object[]>> detailsByGame = new java.util.HashMap<>();
			for (Object[] row : allDetails) {
				long gameId = (row[0] instanceof Number n) ? n.longValue() : 0L;
				detailsByGame.computeIfAbsent(gameId, k -> new java.util.ArrayList<>()).add(row);
			}
			List<Double> aim = new java.util.ArrayList<>();
			List<Double> burst = new java.util.ArrayList<>();
			List<Double> first = new java.util.ArrayList<>();
			List<Double> pace = new java.util.ArrayList<>();
			for (List<Object[]> gameDetails : detailsByGame.values()) {
				gameDetails.sort(java.util.Comparator.comparingInt(r -> {
					Object o = r[1];
					return (o instanceof Number n) ? n.intValue() : 0;
				}));
				int gN = gameDetails.size();
				double sumCC = 0, sumBurst = 0;
				List<Integer> gTotals = new java.util.ArrayList<>();
				for (Object[] d : gameDetails) {
					int cc = ((Number) d[2]).intValue(), cy = ((Number) d[3]).intValue(),
							cok = ((Number) d[4]).intValue();
					sumCC += cc;
					sumBurst += cy + cok;
					gTotals.add(cc + cy + cok);
				}
				aim.add(sumCC / gN);
				burst.add(sumBurst / gN);
				first.add((double) gTotals.get(0));
				if (gN >= 3) {
					double mean = gTotals.stream().mapToInt(Integer::intValue).average().orElse(0);
					double variance = gTotals.stream().mapToDouble(t -> Math.pow(t - mean, 2)).sum() / gN;
					pace.add(Math.sqrt(variance));
				}
			}
			return FeedbackBands.builder()
					.aim(bandOf(aim))
					.burst(bandOf(burst))
					.first(bandOf(first))
					.pace(bandOf(pace))
					.n(detailsByGame.size())
					.build();
		}

		private int[] bandOf(List<Double> values) {
			int[] band = new int[4];
			if (values.isEmpty()) {
				return band;
			}
			List<Double> sorted = new java.util.ArrayList<>(values);
			sorted.sort(Double::compareTo);
			int n = sorted.size();
			band[0] = (int) Math.round(sorted.get((int) Math.ceil(0.10 * n) - 1));
			band[1] = (int) Math.round(sorted.get((int) Math.ceil(0.30 * n) - 1));
			band[2] = (int) Math.round(sorted.get((int) Math.ceil(0.50 * n) - 1));
			band[3] = (int) Math.round(sorted.get((int) Math.ceil(0.70 * n) - 1));
			return band;
		}

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

			Member owner = Member.builder()
					.role(Member.Role.MEMBER)
					.name("홍길동")
					.department("컴퓨터공학과")
					.build();

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(memberRepository.findById(1L)).willReturn(Optional.of(owner));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L))
					.willReturn(List.of(detail));
			given(singleGameStatsService.getPopulation(6)).willReturn(emptyPopulation());
			given(singleGameStatsService.getHeadCount(6)).willReturn(100L);
			given(singleGameStatsService.getDeptPopulation(6, "컴퓨터공학과")).willReturn(emptyPopulation());
			given(singleGameStatsService.getDeptHeadCount(6, "컴퓨터공학과")).willReturn(20L);
			given(singleGameStatsService.getFeedbackBands(6)).willReturn(emptyBands());
			given(singleGameRepository.countBetterPersons(6, 12000)).willReturn(4L);
			given(singleGameRepository.countBetterDeptPersons(6, "컴퓨터공학과", 12000)).willReturn(1L);

			AnalysisResponse response = singleGameService.getAnalysis(1L, 1L);

			assertThat(response.getGameId()).isEqualTo(1L);
			assertThat(response.isOwner()).isTrue();
			assertThat(response.isMember()).isTrue();
			assertThat(response.getRecord().getTTotal()).isEqualTo(12000);
			assertThat(response.getRecord().getTEnterMain()).isEqualTo(2000);
			assertThat(response.getGlobalRank().getRank()).isEqualTo(5);
			assertThat(response.getGlobalRank().getTotalPersons()).isEqualTo(100L);
			assertThat(response.getDepartmentRank().getDepartment()).isEqualTo("컴퓨터공학과");
			assertThat(response.getDepartmentRank().getRank()).isEqualTo(2);
			assertThat(response.getGlobalTimeline()).hasSize(4);
			assertThat(response.getDepartmentTimeline()).hasSize(4);
			assertThat(response.getDetail()).hasSize(4);
			assertThat(response.getFeedbacks()).isNotNull();
			assertThat(response.getFeedbacks().getPrimary().getCode()).isNotNull();
		}

		@Test
		@DisplayName("게스트 조회에서는 학과·피드백·등급이 null이다")
		void it_strips_restricted_fields_for_guest() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(2L).tTotal(12000).tEnterMain(2000)
					.isCompleted(true).totalCourses(6)
					.build();

			SingleGameDetailEntity detail = SingleGameDetailEntity.builder()
					.gameId(1L).sequence(1).tClickCourse(3000).tClickYes(1000).tClickOk(500)
					.build();

			Member owner = Member.builder()
					.role(Member.Role.MEMBER)
					.name("홍길동")
					.department("컴퓨터공학과")
					.build();
			Member guest = Member.builder()
					.role(Member.Role.GUEST)
					.name("게스트")
					.build();

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(memberRepository.findById(2L)).willReturn(Optional.of(owner));
			given(memberRepository.findById(1L)).willReturn(Optional.of(guest));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L))
					.willReturn(List.of(detail));
			given(singleGameStatsService.getPopulation(6)).willReturn(emptyPopulation());
			given(singleGameStatsService.getHeadCount(6)).willReturn(10L);
			given(singleGameRepository.countBetterPersons(6, 12000)).willReturn(0L);

			AnalysisResponse response = singleGameService.getAnalysis(1L, 1L);

			assertThat(response.isOwner()).isFalse();
			assertThat(response.isMember()).isTrue();
			assertThat(response.getRecord().getTTotal()).isEqualTo(12000);
			assertThat(response.getGlobalRank().getRank()).isEqualTo(1);
			assertThat(response.getDepartmentRank()).isNull();
			assertThat(response.getDepartmentTimeline()).isNull();
			assertThat(response.getFeedbacks()).isNull();
			assertThat(response.getGlobalTimeline()).hasSize(4);
			assertThat(response.getDetail()).hasSize(4);
			assertThat(response.getDetail().get(0).getGrade()).isNull();
			assertThat(response.getDetail().get(0).getDepartmentPopulation()).isNull();
			verify(singleGameStatsService, never()).getDeptPopulation(eq(6), eq("컴퓨터공학과"));
			verify(singleGameStatsService, never()).getDeptHeadCount(eq(6), eq("컴퓨터공학과"));
			verify(singleGameStatsService, never()).getFeedbackBands(6);
		}

		@Test
		@DisplayName("존재하지 않는 게임 ID면 예외를 던진다")
		void it_throws_when_game_not_found() {
			given(singleGameRepository.findById(999L)).willReturn(Optional.empty());

			assertThatThrownBy(() -> singleGameService.getAnalysis(999L, 1L))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("god tier physical 피드백이 반환된다")
		void it_returns_god_tier_physical_feedback() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6)
					.build();

			List<SingleGameDetailEntity> details = List.of(
					SingleGameDetailEntity.builder().gameId(1L).sequence(1)
							.tClickCourse(300).tClickYes(100).tClickOk(100).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(2)
							.tClickCourse(300).tClickYes(100).tClickOk(100).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(3)
							.tClickCourse(300).tClickYes(100).tClickOk(100).build()
			);

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(memberRepository.findById(1L)).willReturn(Optional.of(Member.builder().role(Member.Role.MEMBER).name("홍길동").build()));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L)).willReturn(details);
			given(singleGameStatsService.getHeadCount(6)).willReturn(100L);
			given(singleGameRepository.countBetterPersons(6, 5000)).willReturn(0L);

			List<Object[]> allDetails = new java.util.ArrayList<>();
			for (long gId = 1L; gId <= 100L; gId++) {
				int clickCC = (gId == 1L) ? 100 : (int) gId * 50;
				int clickY = (gId == 1L) ? 50 : (int) gId * 20;
				int clickOk = (gId == 1L) ? 50 : (int) gId * 20;
				allDetails.add(new Object[]{gId, 1, clickCC, clickY, clickOk});
			}
			given(singleGameStatsService.getPopulation(6)).willReturn(populationWithEnterMain(100, 300, 600, 1000));
			given(singleGameStatsService.getFeedbackBands(6)).willReturn(bandsFromRawDetails(allDetails));

			AnalysisResponse response = singleGameService.getAnalysis(1L, 1L);

			assertThat(response.getFeedbacks().getPrimary().getCode()).isEqualTo("GOD_TIER_PHYSICAL");
			assertThat(response.getFeedbacks().getPrimary().getMessage()).contains("압도적이고 완벽한 피지컬");
		}

		@Test
		@DisplayName("피드백 코드 PHYSICAL_UPGRADE_NEEDED가 반환된다")
		void it_returns_physical_upgrade_needed_feedback() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(50000).tEnterMain(2000)
					.isCompleted(true).totalCourses(6)
					.build();

			List<SingleGameDetailEntity> details = List.of(
					SingleGameDetailEntity.builder().gameId(1L).sequence(1)
							.tClickCourse(5000).tClickYes(2000).tClickOk(2000).build()
			);

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(memberRepository.findById(1L)).willReturn(Optional.of(Member.builder().role(Member.Role.MEMBER).name("홍길동").build()));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L)).willReturn(details);
			given(singleGameStatsService.getHeadCount(6)).willReturn(10L);
			given(singleGameRepository.countBetterPersons(6, 50000)).willReturn(0L);

			List<Object[]> allDetails = new java.util.ArrayList<>();
			for (long gId = 1L; gId <= 10L; gId++) {
				int cc = (gId == 1L) ? 5000 : (int) gId * 100;
				int cy = (gId == 1L) ? 2000 : (int) gId * 50;
				int cok = (gId == 1L) ? 2000 : (int) gId * 50;
				allDetails.add(new Object[]{gId, 1, cc, cy, cok});
			}
			given(singleGameStatsService.getPopulation(6)).willReturn(populationWithEnterMain(100, 300, 600, 1000));
			given(singleGameStatsService.getFeedbackBands(6)).willReturn(bandsFromRawDetails(allDetails));

			AnalysisResponse response = singleGameService.getAnalysis(1L, 1L);

			assertThat(response.getFeedbacks().getPrimary().getCode()).isEqualTo("PHYSICAL_UPGRADE_NEEDED");
		}

		@Test
		@DisplayName("피드백 코드 FAST_BUT_INACCURATE가 반환된다")
		void it_returns_fast_but_inaccurate_feedback() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(20000).tEnterMain(2000)
					.isCompleted(true).totalCourses(6)
					.build();

			List<SingleGameDetailEntity> details = List.of(
					SingleGameDetailEntity.builder().gameId(1L).sequence(1)
							.tClickCourse(5000).tClickYes(50).tClickOk(50).build()
			);

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(memberRepository.findById(1L)).willReturn(Optional.of(Member.builder().role(Member.Role.MEMBER).name("홍길동").build()));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L)).willReturn(details);
			given(singleGameStatsService.getHeadCount(6)).willReturn(10L);
			given(singleGameRepository.countBetterPersons(6, 20000)).willReturn(0L);

			List<Object[]> allDetails = new java.util.ArrayList<>();
			for (long gId = 1L; gId <= 10L; gId++) {
				int cc = (gId == 1L) ? 5000 : (int) gId * 100;
				int cy = (gId == 1L) ? 50 : (int) gId * 500;
				int cok = (gId == 1L) ? 50 : (int) gId * 500;
				allDetails.add(new Object[]{gId, 1, cc, cy, cok});
			}
			given(singleGameStatsService.getPopulation(6)).willReturn(populationWithEnterMain(100, 300, 600, 1000));
			given(singleGameStatsService.getFeedbackBands(6)).willReturn(bandsFromRawDetails(allDetails));

			AnalysisResponse response = singleGameService.getAnalysis(1L, 1L);

			assertThat(response.getFeedbacks().getPrimary().getCode()).isEqualTo("FAST_BUT_INACCURATE");
		}

		@Test
		@DisplayName("피드백 코드 PERFECT_ENTRY_START가 반환된다")
		void it_returns_perfect_entry_start_feedback() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(600).tEnterMain(100)
					.isCompleted(true).totalCourses(6)
					.build();

			List<SingleGameDetailEntity> details = List.of(
					SingleGameDetailEntity.builder().gameId(1L).sequence(1)
							.tClickCourse(500).tClickYes(50).tClickOk(50).build()
			);

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(memberRepository.findById(1L)).willReturn(Optional.of(Member.builder().role(Member.Role.MEMBER).name("홍길동").build()));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L)).willReturn(details);
			given(singleGameStatsService.getHeadCount(6)).willReturn(10L);
			given(singleGameRepository.countBetterPersons(6, 600)).willReturn(0L);

			List<Object[]> allDetails = new java.util.ArrayList<>();
			for (long gId = 1L; gId <= 10L; gId++) {
				int cc = (gId == 1L) ? 500 : (int) gId * 100;
				int cy = (gId == 1L) ? 50 : (int) gId * 200;
				int cok = (gId == 1L) ? 50 : (int) gId * 200;
				allDetails.add(new Object[]{gId, 1, cc, cy, cok});
			}
			given(singleGameStatsService.getPopulation(6)).willReturn(populationWithEnterMain(100, 300, 600, 1000));
			given(singleGameStatsService.getFeedbackBands(6)).willReturn(bandsFromRawDetails(allDetails));

			AnalysisResponse response = singleGameService.getAnalysis(1L, 1L);

			assertThat(response.getFeedbacks().getPrimary().getCode()).isEqualTo("PERFECT_ENTRY_START");
		}

		@Test
		@DisplayName("피드백 코드 MACHINE_LIKE_PACE가 반환된다 (N>=3)")
		void it_returns_machine_like_pace_feedback() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(3000).tEnterMain(1000)
					.isCompleted(true).totalCourses(6)
					.build();

			List<SingleGameDetailEntity> details = List.of(
					SingleGameDetailEntity.builder().gameId(1L).sequence(1).tClickCourse(500).tClickYes(250).tClickOk(250).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(2).tClickCourse(500).tClickYes(250).tClickOk(250).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(3).tClickCourse(500).tClickYes(250).tClickOk(250).build()
			);

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(memberRepository.findById(1L)).willReturn(Optional.of(Member.builder().role(Member.Role.MEMBER).name("홍길동").build()));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L)).willReturn(details);
			given(singleGameStatsService.getHeadCount(6)).willReturn(10L);
			given(singleGameRepository.countBetterPersons(6, 3000)).willReturn(0L);

			List<Object[]> allDetails = new java.util.ArrayList<>();
			for (long gId = 1L; gId <= 10L; gId++) {
				for (int seq = 1; seq <= 3; seq++) {
					int cc = (gId == 1L) ? 500 : (int) gId * 100;
					int cy = (gId == 1L) ? 250 : (int) gId * 50 + seq * 10;
					int cok = (gId == 1L) ? 250 : (int) gId * 50;
					allDetails.add(new Object[]{gId, seq, cc, cy, cok});
				}
			}
			given(singleGameStatsService.getPopulation(6)).willReturn(populationWithEnterMain(100, 300, 600, 1000));
			given(singleGameStatsService.getFeedbackBands(6)).willReturn(bandsFromRawDetails(allDetails));

			AnalysisResponse response = singleGameService.getAnalysis(1L, 1L);

			assertThat(response.getFeedbacks().getPrimary().getCode()).isEqualTo("MACHINE_LIKE_PACE");
		}

		@Test
		@DisplayName("N=7 등 홀수 과목 수일 때 피드백이 정상적으로 산출된다")
		void it_evaluates_feedback_correctly_when_n_is_seven() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(6900).tEnterMain(1000)
					.isCompleted(true).totalCourses(7)
					.build();

			List<SingleGameDetailEntity> details = List.of(
					SingleGameDetailEntity.builder().gameId(1L).sequence(1).tClickCourse(1000).tClickYes(250).tClickOk(250).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(2).tClickCourse(1000).tClickYes(250).tClickOk(250).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(3).tClickCourse(500).tClickYes(250).tClickOk(250).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(4).tClickCourse(500).tClickYes(250).tClickOk(250).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(5).tClickCourse(100).tClickYes(100).tClickOk(100).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(6).tClickCourse(100).tClickYes(100).tClickOk(100).build(),
					SingleGameDetailEntity.builder().gameId(1L).sequence(7).tClickCourse(100).tClickYes(100).tClickOk(100).build()
			);

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(memberRepository.findById(1L)).willReturn(Optional.of(Member.builder().role(Member.Role.MEMBER).name("홍길동").build()));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L)).willReturn(details);
			given(singleGameStatsService.getHeadCount(7)).willReturn(10L);
			given(singleGameRepository.countBetterPersons(7, 6900)).willReturn(0L);

			List<Object[]> allDetails = new java.util.ArrayList<>();
			for (long gId = 1L; gId <= 10L; gId++) {
				for (int seq = 1; seq <= 7; seq++) {
					int cc = (gId == 1L) ? new int[]{1000, 1000, 500, 500, 100, 100, 100}[seq - 1] : 200 + (int) gId * 50;
					int cy = (gId == 1L) ? new int[]{250, 250, 250, 250, 100, 100, 100}[seq - 1] : 150 + (int) gId * 30;
					int cok = (gId == 1L) ? new int[]{250, 250, 250, 250, 100, 100, 100}[seq - 1] : 150 + (int) gId * 30;
					allDetails.add(new Object[]{gId, seq, cc, cy, cok});
				}
			}
			given(singleGameStatsService.getPopulation(7)).willReturn(populationWithEnterMain(100, 300, 600, 1000));
			given(singleGameStatsService.getFeedbackBands(7)).willReturn(bandsFromRawDetails(allDetails));

			AnalysisResponse response = singleGameService.getAnalysis(1L, 1L);

			assertThat(response.getFeedbacks()).isNotNull();
			assertThat(response.getFeedbacks().getPrimary()).isNotNull();
			assertThat(response.getFeedbacks().getPrimary().getCode()).isNotBlank();
		}

		@Test
		@DisplayName("백분율이 소수점 첫째 자리로 반올림된다")
		void it_rounds_percentile_to_one_decimal_place() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(5000).tEnterMain(2000)
					.isCompleted(true).totalCourses(6)
					.build();

			List<SingleGameDetailEntity> details = List.of(
					SingleGameDetailEntity.builder().gameId(1L).sequence(1)
							.tClickCourse(500).tClickYes(200).tClickOk(200).build()
			);

			given(singleGameRepository.findById(1L)).willReturn(Optional.of(game));
			given(memberRepository.findById(1L)).willReturn(Optional.of(Member.builder().role(Member.Role.MEMBER).name("홍길동").build()));
			given(singleGameDetailRepository.findByGameIdOrderBySequenceAsc(1L)).willReturn(details);
			given(singleGameStatsService.getHeadCount(6)).willReturn(3200L);
			given(singleGameRepository.countBetterPersons(6, 5000)).willReturn(141L);
			given(singleGameStatsService.getPopulation(6)).willReturn(emptyPopulation());
			given(singleGameStatsService.getFeedbackBands(6)).willReturn(emptyBands());

			AnalysisResponse response = singleGameService.getAnalysis(1L, 1L);

			double percentile = response.getGlobalRank().getPercentile();
			String percentileStr = String.valueOf(percentile);
			if (percentileStr.contains(".")) {
				int decimalPlaces = percentileStr.length() - percentileStr.indexOf(".") - 1;
				assertThat(decimalPlaces).isLessThanOrEqualTo(1);
			}
		}
	}
}
