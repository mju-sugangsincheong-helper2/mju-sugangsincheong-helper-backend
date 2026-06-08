package com.mjusugangsincheonghelper.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.session.delivery.TokenDeliveryStrategy;
import com.mjusugangsincheonghelper.auth.session.device.DeviceSessionService;
import com.mjusugangsincheonghelper.auth.session.token.TokenProvider;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService 테스트")
class SessionServiceTest {

	@InjectMocks
	SessionService sessionService;

	@Mock
	TokenProvider tokenProvider;

	@Mock
	TokenDeliveryStrategy tokenDeliveryStrategy;

	@Mock
	DeviceSessionService deviceSessionService;

	@Mock
	MemberRepository memberRepository;

	@Mock
	MemberDeviceRepository memberDeviceRepository;

	@Nested
	@DisplayName("createSession 메서드는")
	class Describe_createSession {

		@Test
		@DisplayName("세션을 생성하고 토큰을 전달한다")
		void it_creates_session_and_delivers_tokens() {
			AuthenticatedIdentity identity = AuthenticatedIdentity.builder()
					.memberId(1L)
					.build();

			Member member = Member.builder()
					.role(Role.GUEST)
					.name("게스트_abcd")
					.build();
			ReflectionTestUtils.setField(member, "id", 1L);

			HttpServletResponse response = new MockHttpServletResponse();

			given(memberRepository.findById(1L)).willReturn(Optional.of(member));
			given(tokenProvider.createAccessToken(1L, "GUEST")).willReturn("access-token");
			given(tokenProvider.createRefreshToken()).willReturn("refresh-token");
			given(tokenProvider.getRefreshTokenExpiryMs()).willReturn(604800000L);

			SessionResult result = sessionService.createSession(identity, DeviceInfo.builder().build(),
					"fcm-token", response);

			assertThat(result.getMemberId()).isEqualTo(1L);
			assertThat(result.getRole()).isEqualTo("GUEST");
			assertThat(result.getAccessToken()).isEqualTo("access-token");
			assertThat(result.getRefreshToken()).isEqualTo("refresh-token");

			verify(tokenDeliveryStrategy).deliver("access-token", "refresh-token", response);
			verify(deviceSessionService).upsert(eq(1L), eq("refresh-token"), eq("fcm-token"), any(), eq(604800000L));
		}
	}

	@Nested
	@DisplayName("refreshSession 메서드는")
	class Describe_refreshSession {

		@Test
		@DisplayName("유효한 RTK로 새 토큰을 발급한다")
		void it_refreshes_tokens_with_valid_rtk() {
			String refreshToken = "valid-refresh-token";
			HttpServletResponse response = new MockHttpServletResponse();

			MemberDevice device = MemberDevice.builder()
					.memberId(1L)
					.refreshToken(refreshToken)
					.expiresAt(Instant.now().plusSeconds(3600))
					.build();

			Member member = Member.builder()
					.role(Role.MEMBER)
					.name("홍길동")
					.build();
			ReflectionTestUtils.setField(member, "id", 1L);

			given(memberDeviceRepository.findByRefreshToken(refreshToken)).willReturn(Optional.of(device));
			given(memberRepository.findById(1L)).willReturn(Optional.of(member));
			given(tokenProvider.createAccessToken(1L, "MEMBER")).willReturn("new-access-token");
			given(tokenProvider.createRefreshToken()).willReturn("new-refresh-token");

			SessionResult result = sessionService.refreshSession(refreshToken, response);

			assertThat(result.getRole()).isEqualTo("MEMBER");
			assertThat(result.getAccessToken()).isEqualTo("new-access-token");
			assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
		}

		@Test
		@DisplayName("유효하지 않은 RTK면 예외를 발생시킨다")
		void it_throws_exception_with_invalid_rtk() {
			String invalidRefreshToken = "invalid-refresh-token";
			HttpServletResponse response = new MockHttpServletResponse();

			given(memberDeviceRepository.findByRefreshToken(invalidRefreshToken)).willReturn(Optional.empty());

			assertThatThrownBy(() -> sessionService.refreshSession(invalidRefreshToken, response))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
		}
	}
}
