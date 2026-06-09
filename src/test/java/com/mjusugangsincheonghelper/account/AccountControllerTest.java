package com.mjusugangsincheonghelper.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.account.controller.AccountController;
import com.mjusugangsincheonghelper.account.dto.AccountMeResponse;
import com.mjusugangsincheonghelper.account.service.AccountService;
import com.mjusugangsincheonghelper.auth.session.delivery.TokenDeliveryStrategy;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("AccountController 슬라이스 테스트")
class AccountControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountService accountService;

	@MockitoBean
	private TokenDeliveryStrategy tokenDeliveryStrategy;

	@MockitoBean
	private SystemConfigService systemConfigService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void setUp() {
		given(systemConfigService.getRaw(anyString())).willReturn("true");

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
	@DisplayName("GET /api/v1/accounts/me 엔드포인트는")
	class Describe_getMe {

		@Test
		@DisplayName("인증된 사용자의 정보를 조회하여 200 응답을 반환한다")
		void it_returns_200_with_member_info() throws Exception {
			// given
			AccountMeResponse response = AccountMeResponse.builder()
					.memberId(1L)
					.name("홍길동")
					.role("USER")
					.isPrivacyPolicyAgreed(true)
					.build();
			given(accountService.getMe(1L)).willReturn(response);

			// when & then
			mockMvc.perform(get("/api/v1/accounts/me"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.memberId").value(1))
					.andExpect(jsonPath("$.data.name").value("홍길동"))
					.andExpect(jsonPath("$.data.role").value("USER"))
					.andExpect(jsonPath("$.data.privacyPolicyAgreed").value(true));
		}

		@Test
		@DisplayName("사용자 정보를 찾지 못하면 404 응답을 반환한다")
		void it_returns_404_when_member_not_found() throws Exception {
			// given
			given(accountService.getMe(1L))
					.willThrow(new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

			// when & then
			mockMvc.perform(get("/api/v1/accounts/me"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("AUTH_007"));
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/accounts/me 엔드포인트는")
	class Describe_withdraw {

		@Test
		@DisplayName("인증된 사용자의 계정을 삭제(탈퇴)하고 200 응답을 반환한다")
		void it_withdraws_member_and_returns_200() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/accounts/me")
							.with(csrf()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").doesNotExist());

			verify(accountService).withdraw(1L);
			verify(tokenDeliveryStrategy).clear(any());
		}
	}
}
