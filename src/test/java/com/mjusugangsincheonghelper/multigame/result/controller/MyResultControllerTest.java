package com.mjusugangsincheonghelper.multigame.result.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundRecordResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundResult;
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

@WebMvcTest(MyResultController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("MyResultController 슬라이스 테스트")
class MyResultControllerTest {

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
	@DisplayName("GET /api/v1/multigame/me/results 엔드포인트는")
	class Describe_myRecords {

		@Test
		@DisplayName("내 라운드 결과 목록을 페이징하여 반환한다")
		void it_returns_my_records() throws Exception {
			given(roundResultService.myRecords(anyLong(), eq(0), eq(10)))
					.willReturn(new PageImpl<>(List.of(MyRoundRecordResponse.builder()
							.multigameId("20260801120000")
							.createdAt(Instant.parse("2026-08-01T12:01:00Z"))
							.results(List.of(MyRoundResult.builder()
									.subjectId(2)
									.status("SUCCESS")
									.createdAt(Instant.parse("2026-08-01T12:01:00Z"))
									.build()))
							.build()), PageRequest.of(0, 10), 1));

			mockMvc.perform(get("/api/v1/multigame/me/results")
							.param("page", "0")
							.param("size", "10"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].multigameId").value("20260801120000"))
					.andExpect(jsonPath("$.data[0].results[0].subjectId").value(2))
					.andExpect(jsonPath("$.data[0].results[0].status").value("SUCCESS"))
					.andExpect(jsonPath("$.page.pageNumber").value(0));
		}
	}
}
