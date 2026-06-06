package com.mjusugangsincheonghelper.auth.authorization.consent;

import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.ErrorResponseEnvelope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrivacyConsentFilter extends OncePerRequestFilter {

	private final MemberAgreementService memberAgreementService;
	private final JsonMapper jsonMapper;

	private static final List<String> WHITELIST_PATTERNS = List.of(
			"/api/",
			"/swagger-ui/",
			"/v3/api-docs",
			"/actuator/"
	);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		for (String pattern : WHITELIST_PATTERNS) {
			if (path.startsWith(pattern)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
			filterChain.doFilter(request, response);
			return;
		}

		boolean isGuest = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(auth -> auth.equals("ROLE_" + Role.GUEST.name()));

		if (isGuest) {
			filterChain.doFilter(request, response);
			return;
		}

		Long memberId = (Long) authentication.getPrincipal();

		if (!memberAgreementService.isAgreed(memberId)) {
			log.debug("Privacy consent required for memberId={}", memberId);
			writeErrorResponse(response, ErrorCode.AUTH_PRIVACY_POLICY_REQUIRED);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		ErrorResponseEnvelope errorResponse = ErrorResponseEnvelope.from(errorCode);
		response.getWriter().write(jsonMapper.writeValueAsString(errorResponse));
	}
}
