package com.mjusugangsincheonghelper.global.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.auth.session.token.TokenProvider;
import com.mjusugangsincheonghelper.auth.session.token.TokenProvider.TokenClaims;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.ErrorResponseEnvelope;
import com.mjusugangsincheonghelper.global.security.token.TokenExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final TokenProvider tokenProvider;
	private final TokenExtractor tokenExtractor;
	private final ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String accessToken = tokenExtractor.extract(request);
		if (accessToken != null) {
			try {
				TokenClaims claims = tokenProvider.parseAccessToken(accessToken);
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						claims.memberId(),
						null,
						Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + claims.role()))
				);
				SecurityContextHolder.getContext().setAuthentication(authentication);

				if ("MEMBER".equals(claims.role()) && !claims.agreed() && !isConsentExemptPath(request)) {
					response.setStatus(HttpStatus.FORBIDDEN.value());
					response.setContentType(MediaType.APPLICATION_JSON_VALUE);
					objectMapper.writeValue(response.getWriter(), ErrorResponseEnvelope.from(ErrorCode.AUTH_PRIVACY_POLICY_REQUIRED));
					return;
				}
			} catch (Exception e) {
				log.debug("Invalid access token: {}", e.getMessage());
			}
		}

		filterChain.doFilter(request, response);
	}

	private boolean isConsentExemptPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.contains("/auth/privacy/agree")
				|| path.contains("/auth/refresh")
				|| path.contains("/auth/logout");
	}
}
