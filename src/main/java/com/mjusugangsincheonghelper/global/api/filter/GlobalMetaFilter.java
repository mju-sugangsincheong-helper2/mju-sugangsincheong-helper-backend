package com.mjusugangsincheonghelper.global.api.filter;

import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.CustomResponseMetaContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class GlobalMetaFilter extends OncePerRequestFilter {

	private final ClientInfoExtractor clientInfoExtractor;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = UUID.randomUUID().toString();
		String apiVersion = resolveApiVersion(request.getRequestURI());
		Instant startedAt = Instant.now();
		String ipAddress = clientInfoExtractor.resolveIpAddress(request);
		String userAgent = clientInfoExtractor.resolveUserAgent(request);

		CustomResponseMetaContextHolder.initialize(
				requestId,
				apiVersion,
				request.getRequestURI(),
				request.getMethod(),
				ipAddress,
				userAgent,
				startedAt
		);

		response.setHeader("X-Request-Id", requestId);
		if (apiVersion != null) {
			response.setHeader("X-Api-Version", apiVersion);
		}

		try {
			filterChain.doFilter(request, response);
		} finally {
			CustomResponseMetaContextHolder.clear();
		}
	}

	private String resolveApiVersion(String uri) {
		if (uri == null) {
			return null;
		}
		String[] segments = uri.split("/");
		if (segments.length > 2 && "api".equals(segments[1])) {
			return segments[2];
		}
		return null;
	}
}
