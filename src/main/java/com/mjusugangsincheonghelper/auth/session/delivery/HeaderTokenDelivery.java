package com.mjusugangsincheonghelper.auth.session.delivery;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class HeaderTokenDelivery implements TokenDeliveryStrategy {

	@Override
	public void deliver(String accessToken, String refreshToken, HttpServletResponse response) {
	}

	@Override
	public void clear(HttpServletResponse response) {
	}
}
