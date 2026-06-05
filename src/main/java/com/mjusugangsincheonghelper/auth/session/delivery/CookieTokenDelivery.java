package com.mjusugangsincheonghelper.auth.session.delivery;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class CookieTokenDelivery implements TokenDeliveryStrategy {

	@Override
	public void deliver(String accessToken, String refreshToken, HttpServletResponse response) {
		response.addHeader("Set-Cookie", createCookie("access_token", accessToken, 3600000).toString());
		response.addHeader("Set-Cookie", createCookie("refresh_token", refreshToken, 604800000).toString());
	}

	@Override
	public void clear(HttpServletResponse response) {
		response.addHeader("Set-Cookie", clearCookie("access_token").toString());
		response.addHeader("Set-Cookie", clearCookie("refresh_token").toString());
	}

	private ResponseCookie createCookie(String name, String value, long maxAgeMs) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ofMillis(maxAgeMs))
				.build();
	}

	private ResponseCookie clearCookie(String name) {
		return ResponseCookie.from(name, "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(0)
				.build();
	}
}
