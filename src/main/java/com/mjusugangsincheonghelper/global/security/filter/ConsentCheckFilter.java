package com.mjusugangsincheonghelper.global.security.filter;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.security.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ConsentCheckFilter extends OncePerRequestFilter {

	private final ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long) {
			boolean isMemberOrAbove = auth.getAuthorities().stream()
					.map(GrantedAuthority::getAuthority)
					.anyMatch(role -> "ROLE_MEMBER".equals(role) || "ROLE_ADMIN".equals(role));

			if (isMemberOrAbove) {
				Object agreedAttr = request.getAttribute("privacyAgreed");
				boolean agreed = agreedAttr == null || Boolean.TRUE.equals(agreedAttr);

				if (!agreed && !isConsentExemptPath(request)) {
					// 403 + AUTH_PRIVACY_POLICY_REQUIRED JSON 봉투 (SecurityErrorWriter 사용)
					SecurityErrorWriter.write(response, objectMapper, ErrorCode.AUTH_PRIVACY_POLICY_REQUIRED);
					return;
				}
			}
		}

		filterChain.doFilter(request, response);
	}

	private boolean isConsentExemptPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.contains("/auth/privacy/agree")
				|| path.contains("/auth/logout");
	}
}
