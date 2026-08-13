package com.mjusugangsincheonghelper.auth.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mjusugangsincheonghelper.account.service.AccountAgreementService;
import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.session.SessionResult;
import com.mjusugangsincheonghelper.auth.session.SessionService;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MergeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@WithMockUser
@DisplayName("MergeController 슬라이스 테스트")
class MergeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MergeService mergeService;

	@MockitoBean
	private SessionService sessionService;

	@MockitoBean
	private AccountAgreementService accountAgreementService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@Nested
	@DisplayName("POST /api/v1/auth/login/google/merge 엔드포인트는")
	class Describe_merge {

		@Test
		@DisplayName("클라이언트가 보낸 device(firebaseCloudMessagingRegistrationToken 포함)를 그대로 세션 생성에 전달한다")
		void it_passes_client_device_with_fcm_token_to_session_creation() throws Exception {
			AuthenticatedIdentity identity = AuthenticatedIdentity.builder().memberId(2L).build();
			given(mergeService.merge("ticket-123")).willReturn(identity);
			given(sessionService.createSession(any(), any(), any())).willReturn(
					SessionResult.builder()
							.memberId(2L)
							.role("MEMBER")
							.name("홍길동")
							.position("pos")
							.department("dept")
							.sessionAccessToken("at")
							.sessionRefreshToken("rt")
							.build());

			mockMvc.perform(post("/api/v1/auth/login/google/merge")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
									  "mergeTicket": "ticket-123",
									  "device": {
									    "name": "Chrome",
									    "os": "Android 14",
									    "product": "Pixel 8",
									    "firebaseCloudMessagingRegistrationToken": "fcm-xyz",
									    "ua": "Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/120.0.0.0 Mobile Safari/537.36"
									  }
									}
									"""))
					.andExpect(status().isOk());

			ArgumentCaptor<DeviceInfo> deviceCaptor = ArgumentCaptor.forClass(DeviceInfo.class);
			verify(sessionService).createSession(any(), deviceCaptor.capture(), any());

			DeviceInfo saved = deviceCaptor.getValue();
			assertThat(saved.getName()).isEqualTo("Chrome");
			assertThat(saved.getOs()).isEqualTo("Android 14");
			assertThat(saved.getProduct()).isEqualTo("Pixel 8");
			assertThat(saved.getFirebaseCloudMessagingRegistrationToken()).isEqualTo("fcm-xyz");
		}
	}
}
