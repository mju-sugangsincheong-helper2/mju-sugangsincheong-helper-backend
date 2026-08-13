package com.mjusugangsincheonghelper.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenDeleteRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenRegisterRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenResponse;
import com.mjusugangsincheonghelper.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("NotificationController 테스트")
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private NotificationService notificationService;

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
	@DisplayName("FCM 토큰 등록 API가 성공적으로 200 OK를 반환한다")
	void shouldRegisterFcmToken() throws Exception {
		NotificationTokenRegisterRequest request = NotificationTokenRegisterRequest.builder()
				.firebaseCloudMessagingRegistrationToken("sample-fcm-token")
				.build();

		given(notificationService.registerToken(eq(1L), any(), any()))
				.willReturn(NotificationTokenResponse.of("sample-fcm-token"));

		mockMvc.perform(post("/api/v1/notification/token")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.firebaseCloudMessagingRegistrationToken").value("sample-fcm-token"));
	}

	@Test
	@DisplayName("FCM 토큰 삭제 API가 성공적으로 200 OK를 반환한다")
	void shouldDeleteFcmToken() throws Exception {
		NotificationTokenDeleteRequest request = NotificationTokenDeleteRequest.builder()
				.firebaseCloudMessagingRegistrationToken("sample-fcm-token")
				.build();

		doNothing().when(notificationService).deleteToken(eq(1L), any(), any());

		mockMvc.perform(delete("/api/v1/notification/token")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk());
	}
}
