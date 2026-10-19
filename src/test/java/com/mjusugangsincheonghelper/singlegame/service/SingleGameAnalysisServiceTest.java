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
@DisplayName("SingleGameAnalysisService 단위 테스트")
class SingleGameAnalysisServiceTest {

	@Mock
	private SingleGameRepository singleGameRepository;

	@Mock
	private SingleGameDetailRepository singleGameDetailRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private SingleGameStatsService singleGameStatsService;

	private SingleGameAnalysisService analysisService;

	@BeforeEach
	void setUp() {
		analysisService = new SingleGameAnalysisService(
				singleGameRepository, singleGameDetailRepository, memberRepository, singleGameStatsService,
				new SingleGameFeedbackEngine(), new SingleGameQuantileInterpolator());
	}

	@Nested
	@DisplayName("getAnalysis 메서드는")
	class Describe_getAnalysis {


		private SequenceQuantileTable quantileTableWithEnterMain7(int q05, int q10, int q30, int q50, int q70, int q90, int q95) {
			return SequenceQuantileTable.builder()
					.segmentQuantiles(java.util.Map.of())
					.enterMain(QuantileBand.builder().q05(q05).q10(q10).q30(q30).q50(q50).q70(q70).q90(q90).q95(q95).build())
					.build();
		}

		private QuantileBand quantileBandOf7(List<Double> values) {
			if (values.isEmpty()) {
				return QuantileBand.empty();
			}
			List<Double> sorted = new java.util.ArrayList<>(values);
			sorted.sort(Double::compareTo);
			int n = sorted.size();
			java.util.function.DoubleFunction<Integer> at = p -> (int) Math.round(sorted.get(Math.min(n - 1, Math.max(0, (int) Math.ceil(p * n) - 1))));
			return QuantileBand.builder()
					.q05(at.apply(0.05)).q10(at.apply(0.10)).q30(at.apply(0.30)).q50(at.apply(0.50))
					.q70(at.apply(0.70)).q90(at.apply(0.90)).q95(at.apply(0.95))
					.build();
		}

		private FeedbackQuantileTable feedbackQuantilesFromRawDetails7(List<Object[]> allDetails) {
			Map<Long, List<Object[]>> detailsByGame = new java.util.HashMap<>();
			for (Object[] row : allDetails) {
				long gameId = (row[0] instanceof Number nn) ? nn.longValue() : 0L;
				detailsByGame.computeIfAbsent(gameId, k -> new java.util.ArrayList<>()).add(row);
			}
			List<Double> aim = new java.util.ArrayList<>();
			List<Double> burst = new java.util.ArrayList<>();
			List<Double> first = new java.util.ArrayList<>();
			List<Double> pace = new java.util.ArrayList<>();
			for (List<Object[]> gameDetails : detailsByGame.values()) {
				gameDetails.sort(java.util.Comparator.comparingInt(r -> (r[1] instanceof Number nn) ? nn.intValue() : 0));
				int gN = gameDetails.size();
				double sumCC = 0, sumBurst = 0;
				List<Integer> gTotals = new java.util.ArrayList<>();
				for (Object[] d : gameDetails) {
					int cc = ((Number) d[2]).intValue(), cy = ((Number) d[3]).intValue(), cok = ((Number) d[4]).intValue();
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
			return FeedbackQuantileTable.builder()
					.aimAvg(quantileBandOf7(aim))
					.burstAvg(quantileBandOf7(burst))
					.firstTotal(quantileBandOf7(first))
					.paceStd(quantileBandOf7(pace))
					.sampleSize(detailsByGame.size())
					.build();
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
			given(singleGameStatsService.getGlobalSegmentQuantiles(6)).willReturn(SequenceQuantileTable.empty());
			given(singleGameStatsService.getHeadCount(6)).willReturn(100L);
			given(singleGameStatsService.getDepartmentSegmentQuantiles(6, "컴퓨터공학과")).willReturn(SequenceQuantileTable.empty());
			given(singleGameStatsService.getDeptHeadCount(6, "컴퓨터공학과")).willReturn(20L);
			given(singleGameStatsService.getFeedbackQuantiles(6)).willReturn(FeedbackQuantileTable.empty());
			given(singleGameRepository.countBetterPersons(6, 12000)).willReturn(4L);
			given(singleGameRepository.countBetterDeptPersons(6, "컴퓨터공학과", 12000)).willReturn(1L);

			AnalysisResponse response = analysisService.analyze(1L, 1L);

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
			given(singleGameStatsService.getGlobalSegmentQuantiles(6)).willReturn(SequenceQuantileTable.empty());
			given(singleGameStatsService.getHeadCount(6)).willReturn(10L);
			given(singleGameRepository.countBetterPersons(6, 12000)).willReturn(0L);

			AnalysisResponse response = analysisService.analyze(1L, 1L);

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
			verify(singleGameStatsService, never()).getDepartmentSegmentQuantiles(eq(6), eq("컴퓨터공학과"));
			verify(singleGameStatsService, never()).getDeptHeadCount(eq(6), eq("컴퓨터공학과"));
			verify(singleGameStatsService, never()).getFeedbackQuantiles(6);
		}

		@Test
		@DisplayName("존재하지 않는 게임 ID면 예외를 던진다")
		void it_throws_when_game_not_found() {
			given(singleGameRepository.findById(999L)).willReturn(Optional.empty());

			assertThatThrownBy(() -> analysisService.analyze(999L, 1L))
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
									given(singleGameStatsService.getGlobalSegmentQuantiles(6)).willReturn(quantileTableWithEnterMain7(50, 100, 300, 600, 1000, 1400, 1600));
			given(singleGameStatsService.getFeedbackQuantiles(6)).willReturn(feedbackQuantilesFromRawDetails7(allDetails));

			AnalysisResponse response = analysisService.analyze(1L, 1L);

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
									given(singleGameStatsService.getGlobalSegmentQuantiles(6)).willReturn(quantileTableWithEnterMain7(50, 100, 300, 600, 1000, 1400, 1600));
			given(singleGameStatsService.getFeedbackQuantiles(6)).willReturn(feedbackQuantilesFromRawDetails7(allDetails));

			AnalysisResponse response = analysisService.analyze(1L, 1L);

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
									given(singleGameStatsService.getGlobalSegmentQuantiles(6)).willReturn(quantileTableWithEnterMain7(50, 100, 300, 600, 1000, 1400, 1600));
			given(singleGameStatsService.getFeedbackQuantiles(6)).willReturn(feedbackQuantilesFromRawDetails7(allDetails));

			AnalysisResponse response = analysisService.analyze(1L, 1L);

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
									given(singleGameStatsService.getGlobalSegmentQuantiles(6)).willReturn(quantileTableWithEnterMain7(50, 100, 300, 600, 1000, 1400, 1600));
			given(singleGameStatsService.getFeedbackQuantiles(6)).willReturn(feedbackQuantilesFromRawDetails7(allDetails));

			AnalysisResponse response = analysisService.analyze(1L, 1L);

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
									given(singleGameStatsService.getGlobalSegmentQuantiles(6)).willReturn(quantileTableWithEnterMain7(50, 100, 300, 600, 1000, 1400, 1600));
			given(singleGameStatsService.getFeedbackQuantiles(6)).willReturn(feedbackQuantilesFromRawDetails7(allDetails));

			AnalysisResponse response = analysisService.analyze(1L, 1L);

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
									given(singleGameStatsService.getGlobalSegmentQuantiles(7)).willReturn(quantileTableWithEnterMain7(50, 100, 300, 600, 1000, 1400, 1600));
			given(singleGameStatsService.getFeedbackQuantiles(7)).willReturn(feedbackQuantilesFromRawDetails7(allDetails));

			AnalysisResponse response = analysisService.analyze(1L, 1L);

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
			given(singleGameStatsService.getGlobalSegmentQuantiles(6)).willReturn(SequenceQuantileTable.empty());
			given(singleGameStatsService.getFeedbackQuantiles(6)).willReturn(FeedbackQuantileTable.empty());

			AnalysisResponse response = analysisService.analyze(1L, 1L);

			double percentile = response.getGlobalRank().getPercentile();
			String percentileStr = String.valueOf(percentile);
			if (percentileStr.contains(".")) {
				int decimalPlaces = percentileStr.length() - percentileStr.indexOf(".") - 1;
				assertThat(decimalPlaces).isLessThanOrEqualTo(1);
			}
		}
	}
}
