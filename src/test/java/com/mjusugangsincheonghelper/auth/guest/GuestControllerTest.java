package com.mjusugangsincheonghelper.auth.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest(GuestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@WithMockUser
@DisplayName("GuestController 슬라이스 테스트")
class GuestControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GuestService guestService;

	@MockitoBean
	private SessionService sessionService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@Nested
	@DisplayName("POST /api/v1/auth/guest 엔드포인트는")
	class Describe_createGuest {

		@Test
		@DisplayName("클라이언트가 보낸 device 정보를 그대로 세션 생성에 전달한다")
		void it_passes_client_device_to_session_creation() throws Exception {
			AuthenticatedIdentity identity = AuthenticatedIdentity.builder().memberId(1L).build();
			given(guestService.authenticate()).willReturn(identity);
			given(sessionService.createSession(any(), any(), any())).willReturn(
					SessionResult.builder()
							.memberId(1L)
							.role("GUEST")
							.name("게스트_abcd")
							.sessionAccessToken("at")
							.sessionRefreshToken("rt")
							.build());

			mockMvc.perform(post("/api/v1/auth/guest")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
									  "device": {
									    "name": "Chrome",
									    "version": "151.0.7922.34",
									    "os": "OS X 10.15.7",
									    "ua": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/151.0.0.0 Safari/537.36"
									  }
									}
									"""))
					.andExpect(status().isCreated());

			ArgumentCaptor<DeviceInfo> deviceCaptor = ArgumentCaptor.forClass(DeviceInfo.class);
			verify(sessionService).createSession(any(), deviceCaptor.capture(), any());

			DeviceInfo saved = deviceCaptor.getValue();
			assertThat(saved.getName()).isEqualTo("Chrome");
			assertThat(saved.getVersion()).isEqualTo("151.0.7922.34");
			assertThat(saved.getOs()).isEqualTo("OS X 10.15.7");
			assertThat(saved.getUa()).startsWith("Mozilla/5.0");
		}
	}
}
