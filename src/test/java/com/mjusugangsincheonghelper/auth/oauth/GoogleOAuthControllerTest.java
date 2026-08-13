package com.mjusugangsincheonghelper.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@WebMvcTest(GoogleOAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@WithMockUser
@DisplayName("GoogleOAuthController 슬라이스 테스트")
class GoogleOAuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OAuthStateService oAuthStateService;

	@MockitoBean
	private GoogleOAuthService googleOAuthService;

	@MockitoBean
	private SessionService sessionService;

	@MockitoBean
	private com.mjusugangsincheonghelper.auth.session.token.TokenProvider tokenProvider;

	@MockitoBean
	private com.mjusugangsincheonghelper.global.security.token.HttpTokenExtractor tokenExtractor;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@Nested
	@DisplayName("POST /api/v1/auth/token 엔드포인트는")
	class Describe_tokenExchange {

		@Test
		@DisplayName("요청 body의 device 정보를 그대로 세션 생성에 전달한다 (null 하드코딩 제거)")
		void it_passes_client_device_to_session_creation() throws Exception {
			AuthenticatedIdentity identity = AuthenticatedIdentity.builder().memberId(5L).build();

			given(oAuthStateService.consumeState("state-1")).willReturn(true);
			given(googleOAuthService.authenticate(anyString(), any())).willReturn(
					OAuthAuthenticationResult.success(identity, true));
			given(sessionService.createSession(any(), any(), any())).willReturn(
					SessionResult.builder()
							.memberId(5L)
							.role("MEMBER")
							.name("홍길동")
							.position("pos")
							.department("dept")
							.sessionAccessToken("at")
							.sessionRefreshToken("rt")
							.build());

			mockMvc.perform(post("/api/v1/auth/token")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
									  "code": "auth-code",
									  "state": "state-1",
									  "device": {
									    "name": "Safari",
									    "os": "OS X 10.15.7",
									    "ua": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"
									  }
									}
									"""))
					.andExpect(status().isOk());

			ArgumentCaptor<DeviceInfo> deviceCaptor = ArgumentCaptor.forClass(DeviceInfo.class);
			verify(sessionService).createSession(any(), deviceCaptor.capture(), any());

			DeviceInfo saved = deviceCaptor.getValue();
			assertThat(saved.getName()).isEqualTo("Safari");
			assertThat(saved.getOs()).isEqualTo("OS X 10.15.7");
			assertThat(saved.getUa()).startsWith("Mozilla/5.0");
		}
	}
}
