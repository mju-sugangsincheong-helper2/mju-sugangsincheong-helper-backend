package com.mjusugangsincheonghelper.global.security.interceptor;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ConsentCheckInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long)) {
			return true;
		}

		boolean isMember = auth.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals("ROLE_MEMBER"));

		if (!isMember) {
			return true;
		}

		boolean agreed = Boolean.TRUE.equals(request.getAttribute("privacyAgreed"));

		if (!agreed && !isConsentExemptPath(request)) {
			throw new BaseException(ErrorCode.AUTH_PRIVACY_POLICY_REQUIRED);
		}

		return true;
	}

	private boolean isConsentExemptPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.contains("/auth/privacy/agree")
				|| path.contains("/auth/refresh")
				|| path.contains("/auth/logout");
	}
}
