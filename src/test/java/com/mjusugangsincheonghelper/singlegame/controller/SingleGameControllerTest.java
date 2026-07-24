package com.mjusugangsincheonghelper.singlegame.controller;

import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.AnalysisDetail;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.AnalysisSummary;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.DataBucket;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse.RankInfo;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse.RecordRanking;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse.RankingEntry;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import com.mjusugangsincheonghelper.singlegame.service.SingleGameService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SingleGameController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("SingleGameController 슬라이스 테스트")
class SingleGameControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SingleGameService singleGameService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void setUp() {
		Authentication authentication = mock(Authentication.class);
		given(authentication.getPrincipal()).willReturn(1L);

		SecurityContext securityContext = mock(SecurityContext.class);
		given(securityContext.getAuthentication()).willReturn(authentication);

		SecurityContextHolder.setContext(securityContext);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Nested
	@DisplayName("POST /api/v1/singlegame 엔드포인트는")
	class Describe_saveGame {

		@Test
		@DisplayName("게임 결과를 저장하고 201 응답을 반환한다")
		void it_returns_201_with_saved_game() throws Exception {
			SingleGameSaveResponse serviceResponse = SingleGameSaveResponse.builder()
					.gameId(100L).message("게임 결과가 성공적으로 기록되었습니다.")
					.build();
			given(singleGameService.saveGame(anyLong(), any())).willReturn(serviceResponse);

			mockMvc.perform(post("/api/v1/singlegame")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
										"totalCourses": 6,
										"isCompleted": true,
										"tEnterMain": 2000,
										"details": [
											{
												"sequence": 1,
												"tClickCourse": 1000,
												"tClickYes": 500,
												"tClickOk": 300
											}
										]
									}
									"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.gameId").value(100))
					.andExpect(jsonPath("$.data.message").value("게임 결과가 성공적으로 기록되었습니다."))
					.andExpect(jsonPath("$.meta").exists());
		}

		@Test
		@DisplayName("유효하지 않은 요청이면 400 응답을 반환한다")
		void it_returns_400_on_validation_error() throws Exception {
			String invalidJson = """
					{
						"totalCourses": 0,
						"isCompleted": true,
						"tEnterMain": -1,
						"details": []
					}
					""";

			mockMvc.perform(post("/api/v1/singlegame")
							.contentType(MediaType.APPLICATION_JSON)
							.content(invalidJson))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/singlegame/rank 엔드포인트는")
	class Describe_getRankings {

		@Test
		@DisplayName("랭킹 정보를 반환한다")
		void it_returns_rankings() throws Exception {
			RankingResponse serviceResponse = RankingResponse.builder()
					.totalCourses(6).scope("GLOBAL")
					.rankings(List.of(
							RankingEntry.builder()
									.rank(1).gameId(1L).name("홍길동")
									.department("컴퓨터공학과").tTotal(5000).tEnterMain(2000)
									.build()
					))
					.build();
			given(singleGameService.getRankings(anyInt(), anyString(), anyLong()))
					.willReturn(serviceResponse);

			mockMvc.perform(get("/api/v1/singlegame/rank")
							.param("totalCourses", "6")
							.param("scope", "GLOBAL"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalCourses").value(6))
					.andExpect(jsonPath("$.data.rankings[0].name").value("홍길동"))
					.andExpect(jsonPath("$.meta").exists());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/singlegame/my 엔드포인트는")
	class Describe_getMyRecords {

		@Test
		@DisplayName("내 게임 기록 목록을 페이징하여 반환한다")
		void it_returns_my_records_page() throws Exception {
			MyRecordResponse record = MyRecordResponse.builder()
					.gameId(1L).totalCourses(6).completed(true)
					.tTotal(5000).tEnterMain(2000).createdAt(java.time.Instant.now())
					.ranking(RecordRanking.builder()
							.global(RankInfo.builder().rank(1).totalParticipants(50).percentile(0).build())
							.department(RankInfo.builder().rank(1).totalParticipants(10).percentile(0).build())
							.build())
					.build();
			given(singleGameService.getMyRecords(anyLong(), anyInt(), anyInt()))
					.willReturn(new PageImpl<>(List.of(record)));

			mockMvc.perform(get("/api/v1/singlegame/my")
							.param("page", "0")
							.param("size", "10"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].gameId").value(1))
					.andExpect(jsonPath("$.data.content[0].tTotal").value(5000))
					.andExpect(jsonPath("$.meta").exists());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/singlegame/{gameId}/analysis 엔드포인트는")
	class Describe_getAnalysis {

		@Test
		@DisplayName("게임 분석 결과를 반환한다")
		void it_returns_analysis() throws Exception {
			AnalysisResponse serviceResponse = AnalysisResponse.builder()
					.gameId(1L).totalCourses(6).completed(true)
					.summary(AnalysisSummary.builder()
							.totalTime(12000).globalRank(5).globalPercentile(4.0)
							.purePhysicalAverage(3000).entryPrecision(2000)
							.initialSprintSpeed(500).paceDeviation(100.0)
							.feedbackCode("GOD_TIER_PHYSICAL")
							.feedbackMessage("압도적이고 완벽한 피지컬! 에이밍과 팝업 연타 모두 최상위권입니다. 수강신청 실패는 당신의 사전에 없습니다.")
							.build())
					.details(List.of(
							AnalysisDetail.builder()
									.sequence(1)
									.mine(DataBucket.builder().clickCourse(3000).clickYes(1000).clickOk(500).total(4500).build())
									.p10(DataBucket.builder().clickCourse(0).clickYes(0).clickOk(0).total(0).build())
									.p30(DataBucket.builder().clickCourse(0).clickYes(0).clickOk(0).total(0).build())
									.p50(DataBucket.builder().clickCourse(0).clickYes(0).clickOk(0).total(0).build())
									.p70(DataBucket.builder().clickCourse(0).clickYes(0).clickOk(0).total(0).build())
									.p100(DataBucket.builder().clickCourse(0).clickYes(0).clickOk(0).total(0).build())
									.build()
					))
					.build();
			given(singleGameService.getAnalysis(1L)).willReturn(serviceResponse);

			mockMvc.perform(get("/api/v1/singlegame/1/analysis"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.gameId").value(1))
					.andExpect(jsonPath("$.data.summary.feedbackCode").value("GOD_TIER_PHYSICAL"))
					.andExpect(jsonPath("$.data.details[0].sequence").value(1))
					.andExpect(jsonPath("$.meta").exists());
		}
	}
}
