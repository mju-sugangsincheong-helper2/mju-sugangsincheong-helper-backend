package com.mjusugangsincheonghelper.auth.authentication.token;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class BearerTokenExtractor implements TokenExtractor {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	@Override
	public String extract(HttpServletRequest request) {
		String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
		if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
			return bearerToken.substring(BEARER_PREFIX.length());
		}
		return null;
	}
}