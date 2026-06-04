package com.mjusugangsincheonghelper.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.auth.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.dto.GuestCreateRequest;
import com.mjusugangsincheonghelper.auth.dto.GuestResponse;
import com.mjusugangsincheonghelper.auth.infrastructure.AuthCookieProvider;
import com.mjusugangsincheonghelper.auth.infrastructure.TokenProvider;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 테스트")
class AuthServiceTest {

	@InjectMocks
	AuthService authService;

	@Mock
	MemberRepository memberRepository;

	@Mock
	MemberAuthRepository memberAuthRepository;

	@Mock
	MemberDeviceRepository memberDeviceRepository;

	@Mock
	DeviceService deviceService;

	@Mock
	TokenProvider tokenProvider;

	@Mock
	AuthCookieProvider authCookieProvider;

	@Nested
	@DisplayName("createGuest 메서드는")
	class Describe_createGuest {

		@Test
		@DisplayName("게스트 계정을 생성하고 쿠키를 설정한다")
		void it_creates_guest_and_sets_cookies() {
			// given
			GuestCreateRequest request = GuestCreateRequest.builder()
				.fcmToken("fcm-token-123")
				.device(DeviceInfo.builder()
					.name("Chrome")
					.version("120.0")
					.os("Windows 10")
					.build())
				.build();

			HttpServletResponse response = new MockHttpServletResponse();

			Member guestMember = Member.builder()
				.role(Role.GUEST)
				.name("게스트_abcd")
				.build();
			ReflectionTestUtils.setField(guestMember, "id", 1L);

			MemberAuth guestAuth = MemberAuth.builder()
				.memberId(1L)
				.authType(AuthType.GUEST_KEY)
				.authKey("guest-key-uuid")
				.build();

			given(memberRepository.save(any(Member.class))).willReturn(guestMember);
			given(memberAuthRepository.save(any(MemberAuth.class))).willReturn(guestAuth);
			given(tokenProvider.createAccessToken(1L, Role.GUEST.name())).willReturn("access-token");
			given(tokenProvider.createRefreshToken()).willReturn("refresh-token");
			given(authCookieProvider.createAccessTokenCookie("access-token", 3600000))
				.willReturn(ResponseCookie.from("access_token", "access-token").build());
			given(authCookieProvider.createRefreshTokenCookie("refresh-token", 604800000))
				.willReturn(ResponseCookie.from("refresh_token", "refresh-token").build());

			ReflectionTestUtils.setField(authService, "refreshTokenExpiryMs", 604800000L);

			// when
			GuestResponse result = authService.createGuest(request, response);

			// then
			assertThat(result.getMemberId()).isEqualTo(1L);
			assertThat(result.getRole()).isEqualTo("GUEST");
			assertThat(result.getName()).startsWith("게스트_");

			verify(memberRepository).save(any(Member.class));
			verify(memberAuthRepository).save(any(MemberAuth.class));
		}
	}

	@Nested
	@DisplayName("refreshToken 메서드는")
	class Describe_refreshToken {

		@Test
		@DisplayName("유효한 RTK로 새 토큰을 발급한다")
		void it_refreshes_tokens_with_valid_rtk() {
			// given
			String refreshToken = "valid-refresh-token";
			HttpServletResponse response = new MockHttpServletResponse();

			MemberDevice device = MemberDevice.builder()
				.memberId(1L)
				.refreshToken(refreshToken)
				.build();

			Member member = Member.builder()
				.role(Role.MEMBER)
				.name("홍길동")
				.build();
			ReflectionTestUtils.setField(member, "id", 1L);

			given(memberDeviceRepository.findByRefreshToken(refreshToken)).willReturn(Optional.of(device));
			given(memberRepository.findById(1L)).willReturn(Optional.of(member));
			given(tokenProvider.createAccessToken(1L, Role.MEMBER.name())).willReturn("new-access-token");
			given(tokenProvider.createRefreshToken()).willReturn("new-refresh-token");
			given(authCookieProvider.createAccessTokenCookie("new-access-token", 3600000))
				.willReturn(ResponseCookie.from("access_token", "new-access-token").build());
			given(authCookieProvider.createRefreshTokenCookie("new-refresh-token", 604800000))
				.willReturn(ResponseCookie.from("refresh_token", "new-refresh-token").build());

			ReflectionTestUtils.setField(authService, "refreshTokenExpiryMs", 604800000L);

			// when
			var result = authService.refreshToken(refreshToken, response);

			// then
			assertThat(result.getStatus()).isEqualTo("success");
			assertThat(result.getRole()).isEqualTo("MEMBER");
		}

		@Test
		@DisplayName("유효하지 않은 RTK면 예외를 발생시킨다")
		void it_throws_exception_with_invalid_rtk() {
			// given
			String invalidRefreshToken = "invalid-refresh-token";
			HttpServletResponse response = new MockHttpServletResponse();

			given(memberDeviceRepository.findByRefreshToken(invalidRefreshToken)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> authService.refreshToken(invalidRefreshToken, response))
				.isInstanceOf(BaseException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
		}
	}
}
