package com.mjusugangsincheonghelper.global.security.filter;

import com.mjusugangsincheonghelper.auth.session.token.TokenProvider;
import com.mjusugangsincheonghelper.auth.session.token.TokenProvider.TokenClaims;
import com.mjusugangsincheonghelper.global.security.token.TokenExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

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
				request.setAttribute("privacyAgreed", claims.agreed());
				request.setAttribute("deviceId", claims.deviceId());
			} catch (Exception e) {
				log.debug("Invalid access token: {}", e.getMessage());
			}
		}

		filterChain.doFilter(request, response);
	}
}
