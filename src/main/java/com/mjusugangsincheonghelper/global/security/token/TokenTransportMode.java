package com.mjusugangsincheonghelper.global.security.token;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 토큰을 주고받을 transport 모드 (app.auth.token-mode).
 *
 * <ul>
 *   <li>{@code cookie}            : HttpOnly 쿠키만 (운영 기본)</li>
 *   <li>{@code header}           : Authorization / X-* 헤더만</li>
 *   <li>{@code header, cookie}   : 헤더 우선 + 쿠키 폴백 (개발/테스트)</li>
 * </ul>
 *
 * <p>플래그 해석을 한 곳에 모아 추출/전달 양쪽에서 동일 규칙을 쓰게 한다.
 */
@Component
public class TokenTransportMode {

	private final boolean useCookie;
	private final boolean useHeader;

	public TokenTransportMode(@Value("${app.auth.token-mode:cookie}") String tokenMode) {
		boolean cookie = false;
		boolean header = false;
		for (String part : tokenMode.split(",")) {
			String mode = part.trim().toLowerCase(Locale.ROOT);
			if (mode.contains("cookie")) {
				cookie = true;
			} else if (mode.contains("header")) {
				header = true;
			}
		}
		if (!cookie && !header) {
			cookie = true; // 미설정/미인식 → 운영 기본(쿠키)
		}
		this.useCookie = cookie;
		this.useHeader = header;
	}

	public boolean usesCookie() {
		return useCookie;
	}

	public boolean usesHeader() {
		return useHeader;
	}
}