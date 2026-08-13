package com.mjusugangsincheonghelper.auth.session.delivery;

import com.mjusugangsincheonghelper.global.security.token.TokenTransportMode;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 토큰 전달 전략. 활성 transport는 {@link TokenTransportMode}가 정한다.
 *
 * <p>쿠키 전송 시 {@code app.auth.cookie-secure}로 Secure 플래그를 제어한다.
 * HTTPS가 아닌 로컬 개발 환경에서는 {@code false}로 두어야 브라우저가 쿠키를 저장한다.
 */
@Component
public class HttpTokenDelivery implements TokenDeliveryStrategy {

	private static final String ACCESS_TOKEN_COOKIE = "session_access_token";
	private static final String REFRESH_TOKEN_COOKIE = "session_refresh_token";

	private final TokenTransportMode mode;
	private final boolean secureCookie;
	private final Duration accessTokenMaxAge;
	private final Duration refreshTokenMaxAge;

	public HttpTokenDelivery(
			TokenTransportMode mode,
			@Value("${app.auth.cookie-secure:true}") boolean secureCookie,
			@Value("${app.auth.cookie-max-age.access:1h}") Duration accessTokenMaxAge,
			@Value("${app.auth.cookie-max-age.refresh:7d}") Duration refreshTokenMaxAge) {
		this.mode = mode;
		this.secureCookie = secureCookie;
		this.accessTokenMaxAge = accessTokenMaxAge;
		this.refreshTokenMaxAge = refreshTokenMaxAge;
	}

	@Override
	public void deliver(String accessToken, String refreshToken, HttpServletResponse response) {
		if (mode.usesCookie()) {
			response.addHeader("Set-Cookie",
					createCookie(ACCESS_TOKEN_COOKIE, accessToken, accessTokenMaxAge).toString());
			response.addHeader("Set-Cookie",
					createCookie(REFRESH_TOKEN_COOKIE, refreshToken, refreshTokenMaxAge).toString());
		}
		if (mode.usesHeader()) {
			response.setHeader("Authorization", "Bearer " + accessToken);
			response.setHeader("X-Access-Token", accessToken);
			response.setHeader("X-Refresh-Token", refreshToken);
		}
	}

	@Override
	public void clear(HttpServletResponse response) {
		if (mode.usesCookie()) {
			response.addHeader("Set-Cookie", clearCookie(ACCESS_TOKEN_COOKIE).toString());
			response.addHeader("Set-Cookie", clearCookie(REFRESH_TOKEN_COOKIE).toString());
		}
		if (mode.usesHeader()) {
			response.setHeader("Authorization", "");
			response.setHeader("X-Access-Token", "");
			response.setHeader("X-Refresh-Token", "");
		}
	}

	private ResponseCookie createCookie(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(secureCookie)
				.sameSite("Lax")
				.path("/")
				.maxAge(maxAge)
				.build();
	}

	private ResponseCookie clearCookie(String name) {
		return ResponseCookie.from(name, "")
				.httpOnly(true)
				.secure(secureCookie)
				.sameSite("Lax")
				.path("/")
				.maxAge(0)
				.build();
	}
}