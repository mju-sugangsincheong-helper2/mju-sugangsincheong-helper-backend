package com.mjusugangsincheonghelper.exchange.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendRequest;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse;
import com.mjusugangsincheonghelper.exchange.dto.RoomToggleRequest;
import com.mjusugangsincheonghelper.exchange.dto.RoomToggleResponse;
import com.mjusugangsincheonghelper.exchange.service.ExchangeService;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExchangeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@WithMockUser
@DisplayName("ExchangeController 슬라이스 테스트")
class ExchangeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private ExchangeService exchangeService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void setUp() {
		// Mock Authentication to return Long principal
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
	@DisplayName("POST /api/v1/exchange/intents 엔드포인트는")
	class Describe_postIntents {

		@Test
		@DisplayName("유효한 요청으로 201 응답을 반환한다")
		void it_returns_201_with_valid_request() throws Exception {
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			IntentCreateResponse response = IntentCreateResponse.builder()
					.intentId(1L)
					.memberId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.isDeleted(false)
					.createdAt(Instant.now())
					.build();

			given(exchangeService.createIntent(any(Long.class), any(IntentCreateRequest.class))).willReturn(response);

			mockMvc.perform(post("/api/v1/exchange/intents")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.intentId").value(1))
					.andExpect(jsonPath("$.data.giveCourseNo").value("10001"))
					.andExpect(jsonPath("$.data.wantCourseNo").value("10002"));
		}

		@Test
		@DisplayName("빈 giveCourseNo로 400 응답을 반환한다")
		void it_returns_400_with_empty_giveCourseNo() throws Exception {
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("")
					.wantCourseNo("10002")
					.build();

			mockMvc.perform(post("/api/v1/exchange/intents")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("GLOBAL_002"));
		}

		@Test
		@DisplayName("중복된 의도면 409 응답을 반환한다")
		void it_returns_409_with_duplicate() throws Exception {
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			given(exchangeService.createIntent(any(Long.class), any(IntentCreateRequest.class)))
					.willThrow(new BaseException(ErrorCode.EXCHANGE_DUPLICATE_INTENT));

			mockMvc.perform(post("/api/v1/exchange/intents")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_007"));
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/exchange/intents/{intentId} 엔드포인트는")
	class Describe_deleteIntents {

		@Test
		@DisplayName("200 응답을 반환한다")
		void it_returns_200() throws Exception {
			IntentDeleteResponse response = IntentDeleteResponse.builder()
					.intentId(1L)
					.isDeleted(true)
					.deletedAt(Instant.now())
					.build();

			given(exchangeService.deleteIntent(any(Long.class), eq(1L))).willReturn(response);

			mockMvc.perform(delete("/api/v1/exchange/intents/1")
							.with(csrf()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.intentId").value(1))
					.andExpect(jsonPath("$.data.deleted").value(true));
		}

		@Test
		@DisplayName("존재하지 않는 의도면 404 응답을 반환한다")
		void it_returns_404_when_not_found() throws Exception {
			given(exchangeService.deleteIntent(any(Long.class), eq(999L)))
					.willThrow(new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_FOUND));

			mockMvc.perform(delete("/api/v1/exchange/intents/999")
							.with(csrf()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_001"));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/exchange/main 엔드포인트는")
	class Describe_getMain {

		@Test
		@DisplayName("200 응답을 반환한다")
		void it_returns_200() throws Exception {
			MainResponse response = MainResponse.builder()
					.myIntents(List.of())
					.recentIntents(List.of())
					.build();

			given(exchangeService.getMain(any(Long.class))).willReturn(response);

			mockMvc.perform(get("/api/v1/exchange/main"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.myIntents").isArray())
					.andExpect(jsonPath("$.data.recentIntents").isArray());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/exchange/intents/recent 엔드포인트는")
	class Describe_getRecentIntents {

		@Test
		@DisplayName("200 응답을 반환한다")
		void it_returns_200() throws Exception {
			RecentIntentsResponse response = RecentIntentsResponse.builder()
					.recentIntents(List.of())
					.build();

			given(exchangeService.getRecentIntents()).willReturn(response);

			mockMvc.perform(get("/api/v1/exchange/intents/recent"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.recentIntents").isArray());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/exchange/rooms/{roomId}/messages 엔드포인트는")
	class Describe_postMessages {

		@Test
		@DisplayName("유효한 요청으로 201 응답을 반환한다")
		void it_returns_201_with_valid_request() throws Exception {
			MessageSendRequest request = MessageSendRequest.builder()
					.content("안녕하세요")
					.build();

			MessageSendResponse response = MessageSendResponse.builder()
					.messageId(1L)
					.roomId(1L)
					.senderId(1L)
					.content("안녕하세요")
					.createdAt(Instant.now())
					.build();

			given(exchangeService.sendMessage(any(Long.class), eq(1L), any(MessageSendRequest.class))).willReturn(response);

			mockMvc.perform(post("/api/v1/exchange/rooms/1/messages")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.messageId").value(1))
					.andExpect(jsonPath("$.data.content").value("안녕하세요"));
		}

		@Test
		@DisplayName("빈 content로 400 응답을 반환한다")
		void it_returns_400_with_empty_content() throws Exception {
			MessageSendRequest request = MessageSendRequest.builder()
					.content("")
					.build();

			mockMvc.perform(post("/api/v1/exchange/rooms/1/messages")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("GLOBAL_002"));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/exchange/rooms/{roomId}/toggle 엔드포인트는")
	class Describe_patchToggle {

		@Test
		@DisplayName("200 응답을 반환한다")
		void it_returns_200() throws Exception {
			RoomToggleRequest request = RoomToggleRequest.builder()
					.isOn(false)
					.build();

			RoomToggleResponse response = RoomToggleResponse.builder()
					.roomId(1L)
					.isOn(false)
					.build();

			given(exchangeService.toggleRoom(any(Long.class), eq(1L), any(RoomToggleRequest.class))).willReturn(response);

			mockMvc.perform(patch("/api/v1/exchange/rooms/1/toggle")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.roomId").value(1))
					.andExpect(jsonPath("$.data.isOn").value(false));
		}
	}
}
