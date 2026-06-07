package com.mjusugangsincheonghelper.auth.session.delivery;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class HeaderTokenDelivery implements TokenDeliveryStrategy {

	@Override
	public void deliver(String accessToken, String refreshToken, HttpServletResponse response) {
		response.addHeader("Set-Cookie", createCookie("access_token", accessToken, 3600000).toString());
		response.addHeader("Set-Cookie", createCookie("refresh_token", refreshToken, 604800000).toString());
		response.setHeader("Authorization", "Bearer " + accessToken);
		response.setHeader("X-Access-Token", accessToken);
		response.setHeader("X-Refresh-Token", refreshToken);
	}

	@Override
	public void clear(HttpServletResponse response) {
		response.addHeader("Set-Cookie", clearCookie("access_token").toString());
		response.addHeader("Set-Cookie", clearCookie("refresh_token").toString());
		response.setHeader("Authorization", "");
		response.setHeader("X-Access-Token", "");
		response.setHeader("X-Refresh-Token", "");
	}

	private ResponseCookie createCookie(String name, String value, long maxAgeMs) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(false)
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ofMillis(maxAgeMs))
				.build();
	}

	private ResponseCookie clearCookie(String name) {
		return ResponseCookie.from(name, "")
				.httpOnly(true)
				.secure(false)
				.sameSite("Lax")
				.path("/")
				.maxAge(0)
				.build();
	}
}
