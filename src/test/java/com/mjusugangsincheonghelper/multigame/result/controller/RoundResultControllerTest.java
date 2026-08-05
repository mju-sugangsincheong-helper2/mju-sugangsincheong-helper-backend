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
		@DisplayName("라운드 상세(게임 시각 + 참여자 수 + 참여 여부 + 처리 시계열)를 반환한다")
		void it_returns_detail() throws Exception {
			given(roundResultService.roundDetail("20260801120000", 1L))
					.willReturn(RoundDetailResponse.builder()
							.multigameId("20260801120000")
							.participantCount(50)
							.capacity(5)
							.participated(true)
							.timeline(List.of(
									RoundDetailResponse.TimelineEntry.builder()
											.participantNo(1)
											.subjectId(2)
											.status("ENQUEUED")
											.seq(3)
											.limit(1)
											.attemptedAt(Instant.parse("2026-08-01T12:00:01Z"))
											.mine(true)
											.build(),
									RoundDetailResponse.TimelineEntry.builder()
											.participantNo(2)
											.subjectId(3)
											.status("SUCCESS")
											.seq(4)
											.limit(2)
											.attemptedAt(Instant.parse("2026-08-01T12:00:02Z"))
											.mine(false)
											.build()))
							.build());

			mockMvc.perform(get("/api/v1/multigame/results/20260801120000"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.multigameId").value("20260801120000"))
					.andExpect(jsonPath("$.data.participantCount").value(50))
					.andExpect(jsonPath("$.data.capacity").value(5))
					.andExpect(jsonPath("$.data.participated").value(true))
					.andExpect(jsonPath("$.data.timeline[0].participantNo").value(1))
					.andExpect(jsonPath("$.data.timeline[0].subjectId").value(2))
					.andExpect(jsonPath("$.data.timeline[0].status").value("ENQUEUED"))
					.andExpect(jsonPath("$.data.timeline[0].seq").value(3))
					.andExpect(jsonPath("$.data.timeline[0].mine").value(true))
					.andExpect(jsonPath("$.data.timeline[1].participantNo").value(2))
					.andExpect(jsonPath("$.data.timeline[1].mine").value(false));
		}

		@Test
		@DisplayName("미참여 라운드는 participated=false와 mine=false인 전체 시계열을 반환한다")
		void it_returns_detail_without_participation() throws Exception {
			given(roundResultService.roundDetail("20260801120000", 1L))
					.willReturn(RoundDetailResponse.builder()
							.multigameId("20260801120000")
							.participantCount(50)
							.participated(false)
							.timeline(List.of(RoundDetailResponse.TimelineEntry.builder()
									.participantNo(1)
									.subjectId(2)
									.status("ENQUEUED")
									.seq(3)
									.limit(1)
									.attemptedAt(Instant.parse("2026-08-01T12:00:01Z"))
									.mine(false)
									.build()))
							.build());

			mockMvc.perform(get("/api/v1/multigame/results/20260801120000"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.participated").value(false))
					.andExpect(jsonPath("$.data.timeline").isArray())
					.andExpect(jsonPath("$.data.timeline[0].participantNo").value(1))
					.andExpect(jsonPath("$.data.timeline[0].mine").value(false));
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
