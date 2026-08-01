package com.mjusugangsincheonghelper.multigame.result.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse.MyDepartment;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse.ParticipationEntry;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse.PerformanceEntry;
import com.mjusugangsincheonghelper.multigame.result.service.RankingService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RankingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("RankingController 슬라이스 테스트")
class RankingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RankingService rankingService;

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

	@Test
	@DisplayName("GET /api/v1/multigame/rankings는 학과 랭킹과 내 학과 순위를 반환한다")
	void it_returns_rankings() throws Exception {
		given(rankingService.rankings(anyLong()))
				.willReturn(MultigameRankingResponse.builder()
						.participation(List.of(ParticipationEntry.builder()
								.department("컴퓨터공학과").participantCount(2)
								.build()))
						.performance(List.of(PerformanceEntry.builder()
								.department("컴퓨터공학과").top70AvgSuccessRate(50.0).participantCount(2)
								.build()))
						.myDepartment(MyDepartment.builder()
								.department("컴퓨터공학과")
								.participationRank(1)
								.performanceRank(1)
								.participantCount(2)
								.top70AvgSuccessRate(50.0)
								.build())
						.build());

		mockMvc.perform(get("/api/v1/multigame/rankings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.participation[0].department").value("컴퓨터공학과"))
				.andExpect(jsonPath("$.data.participation[0].participantCount").value(2))
				.andExpect(jsonPath("$.data.performance[0].top70AvgSuccessRate").value(50.0))
				.andExpect(jsonPath("$.data.myDepartment.participationRank").value(1))
				.andExpect(jsonPath("$.data.myDepartment.performanceRank").value(1))
				.andExpect(jsonPath("$.meta").exists());
	}
}
