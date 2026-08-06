package com.mjusugangsincheonghelper.global.security.token;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰 추출 전략. 활성 transport는 {@link TokenTransportMode}가 정한다.
 *
 * <p>헤더가 활성화되면 Authorization(Bearer)을 우선, 비어 있으면 쿠키를 폴백으로 사용한다.
 * 개발(dev)은 프론트가 쿠키 기반이므로 반드시 쿠키를 함께 활성화해야 한다.
 */
@Component
@RequiredArgsConstructor
public class HttpTokenExtractor implements TokenExtractor {

	private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final TokenTransportMode mode;

	@Override
	public String extract(HttpServletRequest request) {
		if (mode.usesHeader()) {
			String bearer = extractFromBearer(request);
			if (bearer != null) {
				return bearer;
			}
		}
		if (mode.usesCookie()) {
			return extractFromCookie(request);
		}
		return null;
	}

	private String extractFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private String extractFromBearer(HttpServletRequest request) {
		String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
		if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
			return bearerToken.substring(BEARER_PREFIX.length());
		}
		return null;
	}
}