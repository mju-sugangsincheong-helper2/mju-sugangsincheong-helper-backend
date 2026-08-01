package com.mjusugangsincheonghelper.multigame.result.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundResult;
import com.mjusugangsincheonghelper.multigame.result.dto.RoundDetailResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.RoundSummaryResponse;
import com.mjusugangsincheonghelper.multigame.result.service.RoundResultService;
import java.time.Instant;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoundResultController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("RoundResultController 슬라이스 테스트")
class RoundResultControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RoundResultService roundResultService;

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
	@DisplayName("GET /api/v1/multigame/results 엔드포인트는")
	class Describe_rounds {

		@Test
		@DisplayName("라운드 결과 목록을 페이징하여 반환한다")
		void it_returns_rounds_page() throws Exception {
			given(roundResultService.rounds(eq(0), eq(10)))
					.willReturn(new PageImpl<>(List.of(RoundSummaryResponse.builder()
							.multigameId("20260801120000")
							.participantCount(50)
							.capacity(5)
							.createdAt(Instant.parse("2026-08-01T12:01:00Z"))
							.build()), PageRequest.of(0, 10), 1));

			mockMvc.perform(get("/api/v1/multigame/results")
							.param("page", "0")
							.param("size", "10"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].multigameId").value("20260801120000"))
					.andExpect(jsonPath("$.data[0].participantCount").value(50))
					.andExpect(jsonPath("$.page.pageNumber").value(0))
					.andExpect(jsonPath("$.page.pageSize").value(10))
					.andExpect(jsonPath("$.meta").exists());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/multigame/results/{multigameId} 엔드포인트는")
	class Describe_detail {

		@Test
		@DisplayName("라운드 상세(메타 + 분석서 + 내 참여 정보)를 반환한다")
		void it_returns_detail() throws Exception {
			given(roundResultService.roundDetail("20260801120000", 1L))
					.willReturn(RoundDetailResponse.builder()
							.multigameId("20260801120000")
							.participantCount(50)
							.capacity(5)
							.participated(true)
							.myResults(List.of(MyRoundResult.builder()
									.subjectId(2)
									.status("SUCCESS")
									.createdAt(Instant.parse("2026-08-01T12:01:00Z"))
									.build()))
							.myLog(List.of(RoundDetailResponse.AttemptLog.builder()
									.subjectId(2)
									.status("ENQUEUED")
									.seq(3)
									.limit(1)
									.attemptedAt(Instant.parse("2026-08-01T12:00:01Z"))
									.build()))
							.subjects(List.of(RoundDetailResponse.SubjectStat.builder()
									.subjectId(1).applied(10).succeeded(8).competitionRate(2.0)
									.build()))
							.build());

			mockMvc.perform(get("/api/v1/multigame/results/20260801120000"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.multigameId").value("20260801120000"))
					.andExpect(jsonPath("$.data.participated").value(true))
					.andExpect(jsonPath("$.data.myResults[0].subjectId").value(2))
					.andExpect(jsonPath("$.data.myResults[0].status").value("SUCCESS"))
					.andExpect(jsonPath("$.data.myLog[0].subjectId").value(2))
					.andExpect(jsonPath("$.data.myLog[0].status").value("ENQUEUED"))
					.andExpect(jsonPath("$.data.myLog[0].seq").value(3))
					.andExpect(jsonPath("$.data.subjects[0].subjectId").value(1))
					.andExpect(jsonPath("$.data.subjects[0].applied").value(10))
					.andExpect(jsonPath("$.data.subjects[0].competitionRate").value(2.0));
		}

		@Test
		@DisplayName("미참여 라운드는 participated=false와 myResults=[]로 반환한다")
		void it_returns_detail_without_participation() throws Exception {
			given(roundResultService.roundDetail("20260801120000", 1L))
					.willReturn(RoundDetailResponse.builder()
							.multigameId("20260801120000")
							.participantCount(50)
							.capacity(5)
							.participated(false)
							.myResults(List.of())
							.myLog(List.of())
							.subjects(List.of())
							.build());

			mockMvc.perform(get("/api/v1/multigame/results/20260801120000"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.participated").value(false))
					.andExpect(jsonPath("$.data.myResults").isArray())
					.andExpect(jsonPath("$.data.myResults").isEmpty())
					.andExpect(jsonPath("$.data.myLog").isArray());
		}

		@Test
		@DisplayName("라운드가 없으면 404와 MULTIGAME_004를 반환한다")
		void it_returns_404_when_round_absent() throws Exception {
			given(roundResultService.roundDetail(anyString(), eq(1L)))
					.willThrow(new BaseException(ErrorCode.MULTIGAME_RESULT_NOT_FOUND));

			mockMvc.perform(get("/api/v1/multigame/results/20260801120000"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("MULTIGAME_004"));
		}
	}
}
