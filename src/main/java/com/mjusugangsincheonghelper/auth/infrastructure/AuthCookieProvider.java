package com.mjusugangsincheonghelper.auth.infrastructure;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieProvider {

	public ResponseCookie createAccessTokenCookie(String token, long expiryMs) {
		return ResponseCookie.from("access_token", token)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ofMillis(expiryMs))
				.build();
	}

	public ResponseCookie createRefreshTokenCookie(String token, long expiryMs) {
		return ResponseCookie.from("refresh_token", token)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ofMillis(expiryMs))
				.build();
	}

	public ResponseCookie clearAccessTokenCookie() {
		return ResponseCookie.from("access_token", "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(0)
				.build();
	}

	public ResponseCookie clearRefreshTokenCookie() {
		return ResponseCookie.from("refresh_token", "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(0)
				.build();
	}
}
