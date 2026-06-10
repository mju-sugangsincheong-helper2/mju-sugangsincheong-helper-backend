package com.mjusugangsincheonghelper.exchange.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendRequest;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse;
import com.mjusugangsincheonghelper.exchange.service.ExchangeService;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExchangeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("ExchangeController 슬라이스 테스트")
class ExchangeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private ExchangeService exchangeService;

	@MockitoBean
	private SystemConfigService systemConfigService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void setUp() {
		given(systemConfigService.getRaw(anyString())).willReturn("true");

		Authentication authentication = mock(Authentication.class);
		given(authentication.getPrincipal()).willReturn(100L);

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
	class Describe_postCreateIntent {

		@Test
		@DisplayName("유효한 요청으로 201 응답을 반환한다")
		void it_returns_201_with_valid_request() throws Exception {
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			IntentCreateResponse response = IntentCreateResponse.builder()
					.message("교환 의사가 성공적으로 등록되었습니다.")
					.timestamp(Instant.now())
					.intentId(1L)
					.memberId(100L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.isDeleted(false)
					.build();

			given(exchangeService.createIntent(any(), any(IntentCreateRequest.class)))
					.willReturn(response);

			mockMvc.perform(post("/api/v1/exchange/intents")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.intentId").value(1))
					.andExpect(jsonPath("$.data.giveCourseNo").value("10001"))
					.andExpect(jsonPath("$.data.wantCourseNo").value("10002"))
					.andExpect(jsonPath("$.meta").exists());
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
		@DisplayName("빈 wantCourseNo로 400 응답을 반환한다")
		void it_returns_400_with_empty_wantCourseNo() throws Exception {
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("")
					.build();

			mockMvc.perform(post("/api/v1/exchange/intents")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("GLOBAL_002"));
		}

		@Test
		@DisplayName("동일한 과목 번호로 400 응답을 반환한다")
		void it_returns_400_with_same_course() throws Exception {
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10001")
					.build();

			given(exchangeService.createIntent(any(), any(IntentCreateRequest.class)))
					.willThrow(new BaseException(ErrorCode.EXCHANGE_SAME_COURSE));

			mockMvc.perform(post("/api/v1/exchange/intents")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_006"));
		}

		@Test
		@DisplayName("중복된 의도로 409 응답을 반환한다")
		void it_returns_409_with_duplicate_intent() throws Exception {
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			given(exchangeService.createIntent(any(), any(IntentCreateRequest.class)))
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
	class Describe_deleteIntent {

		@Test
		@DisplayName("유효한 요청으로 200 응답을 반환한다")
		void it_returns_200_with_valid_request() throws Exception {
			Long intentId = 1L;

			IntentDeleteResponse response = IntentDeleteResponse.builder()
					.message("교환 의사가 철회되었습니다.")
					.timestamp(Instant.now())
					.intentId(intentId)
					.isDeleted(true)
					.build();

			given(exchangeService.deleteIntent(any(), any()))
					.willReturn(response);

			mockMvc.perform(delete("/api/v1/exchange/intents/" + intentId)
							.with(csrf()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.intentId").value(1))
					.andExpect(jsonPath("$.data.deleted").value(true));
		}

		@Test
		@DisplayName("존재하지 않는 의도로 404 응답을 반환한다")
		void it_returns_404_when_not_found() throws Exception {
			Long intentId = 999L;

			given(exchangeService.deleteIntent(any(), any()))
					.willThrow(new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_FOUND));

			mockMvc.perform(delete("/api/v1/exchange/intents/" + intentId)
							.with(csrf()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_001"));
		}

		@Test
		@DisplayName("다른 사용자의 의도로 403 응답을 반환한다")
		void it_returns_403_when_not_owner() throws Exception {
			Long intentId = 1L;

			given(exchangeService.deleteIntent(any(), any()))
					.willThrow(new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_OWNER));

			mockMvc.perform(delete("/api/v1/exchange/intents/" + intentId)
							.with(csrf()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_003"));
		}

		@Test
		@DisplayName("이미 삭제된 의도로 400 응답을 반환한다")
		void it_returns_400_when_already_deleted() throws Exception {
			Long intentId = 1L;

			given(exchangeService.deleteIntent(any(), any()))
					.willThrow(new BaseException(ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED));

			mockMvc.perform(delete("/api/v1/exchange/intents/" + intentId)
							.with(csrf()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_002"));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/exchange/main 엔드포인트는")
	class Describe_getMain {

		@Test
		@DisplayName("200 응답을 반환한다")
		void it_returns_200() throws Exception {
			MainResponse response = MainResponse.builder()
					.message("메인 상태 조회 성공")
					.timestamp(Instant.now())
					.myIntents(Collections.emptyList())
					.myRooms(Collections.emptyList())
					.build();

			given(exchangeService.getMain(any()))
					.willReturn(response);

			mockMvc.perform(get("/api/v1/exchange/main"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.message").value("메인 상태 조회 성공"))
					.andExpect(jsonPath("$.data.myIntents").isArray())
					.andExpect(jsonPath("$.data.myRooms").isArray());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/exchange/intents/recent 엔드포인트는")
	class Describe_getRecentIntents {

		@Test
		@DisplayName("200 응답을 반환한다")
		void it_returns_200() throws Exception {
			RecentIntentsResponse response = RecentIntentsResponse.builder()
					.message("최근 등록된 교환 의사 조회 성공")
					.timestamp(Instant.now())
					.intents(Collections.emptyList())
					.nextLastIntentId(0L)
					.hasNext(false)
					.build();

			given(exchangeService.getRecentIntents(any(), anyInt()))
					.willReturn(response);

			mockMvc.perform(get("/api/v1/exchange/intents/recent")
							.param("lastIntentId", "0")
							.param("limit", "10"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.message").value("최근 등록된 교환 의사 조회 성공"))
					.andExpect(jsonPath("$.data.intents").isArray());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/exchange/rooms/{roomId}/messages 엔드포인트는")
	class Describe_getMessages {

		@Test
		@DisplayName("200 응답을 반환한다")
		void it_returns_200() throws Exception {
			Long roomId = 1L;

			MessageResponse response = MessageResponse.builder()
					.message("메시지 조회 성공")
					.timestamp(Instant.now())
					.roomId(roomId)
					.messages(Collections.emptyList())
					.nextLastMessageId(0L)
					.hasNext(false)
					.build();

			given(exchangeService.getMessages(any(), any(), any(), anyInt()))
					.willReturn(response);

			mockMvc.perform(get("/api/v1/exchange/rooms/" + roomId + "/messages")
							.param("lastMessageId", "999999999")
							.param("size", "20"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.message").value("메시지 조회 성공"))
					.andExpect(jsonPath("$.data.messages").isArray());
		}

		@Test
		@DisplayName("방 멤버가 아니면 403 응답을 반환한다")
		void it_returns_403_when_not_member() throws Exception {
			Long roomId = 1L;

			given(exchangeService.getMessages(any(), any(), any(), anyInt()))
					.willThrow(new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER));

			mockMvc.perform(get("/api/v1/exchange/rooms/" + roomId + "/messages")
							.param("lastMessageId", "999999999")
							.param("size", "20"))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_005"));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/exchange/rooms/{roomId}/messages 엔드포인트는")
	class Describe_sendMessage {

		@Test
		@DisplayName("유효한 요청으로 201 응답을 반환한다")
		void it_returns_201_with_valid_request() throws Exception {
			Long roomId = 1L;

			MessageSendRequest request = MessageSendRequest.builder()
					.content("테스트 메시지")
					.build();

			MessageSendResponse response = MessageSendResponse.builder()
					.message("메시지가 전송되었습니다.")
					.timestamp(Instant.now())
					.messageId(1L)
					.roomId(roomId)
					.senderId(100L)
					.content("테스트 메시지")
					.createdAt(Instant.now())
					.build();

			given(exchangeService.sendMessage(any(), any(), any(MessageSendRequest.class)))
					.willReturn(response);

			mockMvc.perform(post("/api/v1/exchange/rooms/" + roomId + "/messages")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.messageId").value(1))
					.andExpect(jsonPath("$.data.content").value("테스트 메시지"));
		}

		@Test
		@DisplayName("빈 content로 400 응답을 반환한다")
		void it_returns_400_with_empty_content() throws Exception {
			Long roomId = 1L;

			MessageSendRequest request = MessageSendRequest.builder()
					.content("")
					.build();

			mockMvc.perform(post("/api/v1/exchange/rooms/" + roomId + "/messages")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("GLOBAL_002"));
		}

		@Test
		@DisplayName("방 멤버가 아니면 403 응답을 반환한다")
		void it_returns_403_when_not_member() throws Exception {
			Long roomId = 1L;

			MessageSendRequest request = MessageSendRequest.builder()
					.content("테스트 메시지")
					.build();

			given(exchangeService.sendMessage(any(), any(), any(MessageSendRequest.class)))
					.willThrow(new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_MEMBER));

			mockMvc.perform(post("/api/v1/exchange/rooms/" + roomId + "/messages")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_005"));
		}
	}
}
