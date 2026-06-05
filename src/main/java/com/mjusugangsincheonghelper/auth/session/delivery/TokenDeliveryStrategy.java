package com.mjusugangsincheonghelper.auth.session.delivery;

import jakarta.servlet.http.HttpServletResponse;

public interface TokenDeliveryStrategy {

	void deliver(String accessToken, String refreshToken, HttpServletResponse response);

	void clear(HttpServletResponse response);
}
