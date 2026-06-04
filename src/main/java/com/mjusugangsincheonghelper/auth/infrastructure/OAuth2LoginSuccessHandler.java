package com.mjusugangsincheonghelper.auth.infrastructure;

import com.mjusugangsincheonghelper.auth.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.service.DeviceService;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private static final String GUEST_MEMBER_ID_COOKIE = "guest_member_id";
	private static final String GUEST_FCM_TOKEN_COOKIE = "guest_fcm_token";

	private final TokenProvider tokenProvider;
	private final AuthCookieProvider authCookieProvider;
	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final DeviceService deviceService;

	@Value("${app.oauth2.google.success-redirect-uri}")
	private String successRedirectUri;

	@Value("${app.jwt.refresh-token-expiry-ms}")
	private long refreshTokenExpiryMs;

	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
		String googleSubId = oidcUser.getSubject();

		MemberAuth memberAuth = memberAuthRepository.findByAuthKeyAndAuthType(googleSubId, AuthType.GOOGLE)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		Member member = memberRepository.findById(memberAuth.getMemberId())
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		Long guestMemberId = extractCookieAsLong(request, GUEST_MEMBER_ID_COOKIE);
		String fcmToken = extractCookie(request, GUEST_FCM_TOKEN_COOKIE);

		if (guestMemberId != null) {
			mergeGuestToMember(guestMemberId, member);
		}

		String accessToken = tokenProvider.createAccessToken(member.getId(), member.getRole().name());
		String refreshToken = tokenProvider.createRefreshToken();

		deviceService.upsert(member.getId(), refreshToken, fcmToken, DeviceInfo.builder().build());

		response.addHeader("Set-Cookie", authCookieProvider.createAccessTokenCookie(accessToken, 3600000).toString());
		response.addHeader("Set-Cookie", authCookieProvider.createRefreshTokenCookie(refreshToken, refreshTokenExpiryMs).toString());

		clearCookie(request, response, GUEST_MEMBER_ID_COOKIE);
		clearCookie(request, response, GUEST_FCM_TOKEN_COOKIE);

		getRedirectStrategy().sendRedirect(request, response, successRedirectUri);
	}

	private void mergeGuestToMember(Long guestMemberId, Member member) {
		MemberAuth guestAuth = memberAuthRepository.findByMemberIdAndAuthType(guestMemberId, AuthType.GUEST_KEY)
				.orElse(null);

		if (guestAuth != null) {
			memberAuthRepository.delete(guestAuth);
		}

		memberRepository.deleteById(guestMemberId);
	}

	private Long extractCookieAsLong(HttpServletRequest request, String name) {
		String value = extractCookie(request, name);
		if (value == null) {
			return null;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private String extractCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (name.equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}

	private void clearCookie(HttpServletRequest request, HttpServletResponse response, String name) {
		Cookie cookie = new Cookie(name, "");
		cookie.setMaxAge(0);
		cookie.setPath("/");
		response.addCookie(cookie);
	}
}
