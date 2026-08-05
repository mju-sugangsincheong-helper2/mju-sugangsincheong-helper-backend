package com.mjusugangsincheonghelper.global.security.filter;

import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.ErrorResponseEnvelope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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
					sendErrorResponse(response);
					return;
				}
			}
		}

		filterChain.doFilter(request, response);
	}

	private boolean isConsentExemptPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.contains("/auth/privacy/agree")
				|| path.contains("/auth/logout")
				|| path.startsWith("/actuator/");
	}

	private void sendErrorResponse(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");

		ErrorResponseEnvelope errorEnvelope = ErrorResponseEnvelope.from(ErrorCode.AUTH_PRIVACY_POLICY_REQUIRED);
		String json = objectMapper.writeValueAsString(errorEnvelope);

		response.getWriter().write(json);
	}
}
