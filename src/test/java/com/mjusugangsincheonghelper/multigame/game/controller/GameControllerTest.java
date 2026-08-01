package com.mjusugangsincheonghelper.multigame.game.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.multigame.game.dto.GameApplyResponse;
import com.mjusugangsincheonghelper.multigame.game.dto.GameEnterResponse;
import com.mjusugangsincheonghelper.multigame.game.dto.GameWaitingResponse;
import com.mjusugangsincheonghelper.multigame.game.service.GameSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

@WebMvcTest(GameController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("GameController 슬라이스 테스트")
class GameControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GameSessionService gameSessionService;

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
	@DisplayName("GET /api/v1/multigame/session/waiting-room 엔드포인트는")
	class Describe_waitingRoom {

		@Test
		@DisplayName("게임 상태와 참여 인원을 반환한다")
		void it_returns_waiting_room() throws Exception {
			given(gameSessionService.waitingRoom(anyLong()))
					.willReturn(GameWaitingResponse.builder()
							.multigameId("20260801120000")
							.state("WAITING")
							.participation(23)
							.build());

			mockMvc.perform(get("/api/v1/multigame/session/waiting-room"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.multigameId").value("20260801120000"))
					.andExpect(jsonPath("$.data.state").value("WAITING"))
					.andExpect(jsonPath("$.data.participation").value(23))
					.andExpect(jsonPath("$.meta").exists());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/multigame/session/enter 엔드포인트는")
	class Describe_enter {

		@Test
		@DisplayName("게임 입장 결과를 반환한다")
		void it_returns_enter_result() throws Exception {
			given(gameSessionService.enter(anyLong()))
					.willReturn(GameEnterResponse.builder()
							.multigameId("20260801120000")
							.state("PROGRESS")
							.participation(9)
							.build());

			mockMvc.perform(post("/api/v1/multigame/session/enter"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.state").value("PROGRESS"))
					.andExpect(jsonPath("$.data.participation").value(9));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/multigame/session/leave 엔드포인트는")
	class Describe_leave {

		@Test
		@DisplayName("빈 응답을 반환한다")
		void it_returns_empty() throws Exception {
			mockMvc.perform(post("/api/v1/multigame/session/leave"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.meta").exists());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/multigame/session/apply 엔드포인트는")
	class Describe_apply {

		@Test
		@DisplayName("과목 신청 결과를 반환한다")
		void it_returns_apply_result() throws Exception {
			given(gameSessionService.apply(anyLong(), anyInt()))
					.willReturn(GameApplyResponse.builder()
							.status("SUCCESS")
							.subjectId(1)
							.remaining(2)
							.build());

			mockMvc.perform(post("/api/v1/multigame/session/apply")
							.param("subjectId", "1"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("SUCCESS"))
					.andExpect(jsonPath("$.data.subjectId").value(1))
					.andExpect(jsonPath("$.data.remaining").value(2));
		}

		@Test
		@DisplayName("BLOCKED 결과도 200으로 반환한다")
		void it_returns_blocked_as_ok() throws Exception {
			given(gameSessionService.apply(anyLong(), anyInt()))
					.willReturn(GameApplyResponse.builder()
							.status("BLOCKED")
							.currentState("ENDED")
							.build());

			mockMvc.perform(post("/api/v1/multigame/session/apply")
							.param("subjectId", "3"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("BLOCKED"))
					.andExpect(jsonPath("$.data.currentState").value("ENDED"));
		}
	}
}
