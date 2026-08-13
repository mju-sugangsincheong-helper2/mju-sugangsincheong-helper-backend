package com.mjusugangsincheonghelper.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.account.service.AccountAgreementService;
import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.session.delivery.TokenDeliveryStrategy;
import com.mjusugangsincheonghelper.auth.session.device.DeviceSessionService;
import com.mjusugangsincheonghelper.auth.session.token.RefreshTokenHasher;
import com.mjusugangsincheonghelper.auth.session.token.TokenProvider;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
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

	@Mock
	AccountAgreementService accountAgreementService;

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

			MemberDevice device = MemberDevice.builder()
					.memberId(1L)
					.refreshTokenHash(RefreshTokenHasher.hash("refresh-token"))
					.build();
			ReflectionTestUtils.setField(device, "id", 10L);

			HttpServletResponse response = new MockHttpServletResponse();

			given(memberRepository.findById(1L)).willReturn(Optional.of(member));
			given(accountAgreementService.isAgreed(1L)).willReturn(false);
			given(tokenProvider.createRefreshToken()).willReturn("refresh-token");
			given(tokenProvider.getRefreshTokenExpiryMs()).willReturn(604800000L);
			given(deviceSessionService.upsert(eq(1L), eq("refresh-token"), any(), eq(604800000L))).willReturn(device);
			given(tokenProvider.createAccessToken(1L, "GUEST", false, 10L)).willReturn("access-token");

			SessionResult result = sessionService.createSession(identity, DeviceInfo.builder().build(), response);

			assertThat(result.getMemberId()).isEqualTo(1L);
			assertThat(result.getRole()).isEqualTo("GUEST");
			assertThat(result.getAccessToken()).isEqualTo("access-token");
			assertThat(result.getRefreshToken()).isEqualTo("refresh-token");

			verify(tokenDeliveryStrategy).deliver("access-token", "refresh-token", response);
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
					.refreshTokenHash(RefreshTokenHasher.hash(refreshToken))
					.expiresAt(java.time.Instant.now().plusMillis(3600_000L))
					.build();
			ReflectionTestUtils.setField(device, "id", 10L);

			Member member = Member.builder()
					.role(Role.MEMBER)
					.name("홍길동")
					.build();
			ReflectionTestUtils.setField(member, "id", 1L);

			given(memberDeviceRepository.findByRefreshTokenHash(RefreshTokenHasher.hash(refreshToken))).willReturn(Optional.of(device));
			given(memberRepository.findById(1L)).willReturn(Optional.of(member));
			given(accountAgreementService.isAgreed(1L)).willReturn(false);
			given(tokenProvider.createRefreshToken()).willReturn("new-refresh-token");
			given(tokenProvider.createAccessToken(1L, "MEMBER", false, 10L)).willReturn("new-access-token");

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

			given(memberDeviceRepository.findByRefreshTokenHash(RefreshTokenHasher.hash(invalidRefreshToken))).willReturn(Optional.empty());

			assertThatThrownBy(() -> sessionService.refreshSession(invalidRefreshToken, response))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
		}

		@Test
		@DisplayName("만료된 디바이스는 별도 트랜잭션에서 삭제(purge)하고 예외를 던진다")
		void it_purges_and_throws_for_expired_device() {
			String refreshToken = "expired-refresh-token";
			HttpServletResponse response = new MockHttpServletResponse();

			MemberDevice device = MemberDevice.builder()
					.memberId(1L)
					.refreshTokenHash(RefreshTokenHasher.hash(refreshToken))
					.expiresAt(java.time.Instant.now().minusMillis(1000)) // 과거 = 만료
					.build();
			ReflectionTestUtils.setField(device, "id", 10L);

			given(memberDeviceRepository.findByRefreshTokenHash(RefreshTokenHasher.hash(refreshToken)))
					.willReturn(Optional.of(device));

			assertThatThrownBy(() -> sessionService.refreshSession(refreshToken, response))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_INVALID_REFRESH_TOKEN);

			// 별도 트랜잭션에서 삭제되어야 한다 (rollback 방지)
			verify(deviceSessionService).purgeDevice(10L);
			// 이전 구현의 same-tx delete는 더 이상 호출되지 않는다
			verify(memberDeviceRepository, never()).delete(any(MemberDevice.class));
		}
	}
}
