package com.mjusugangsincheonghelper.global.api.filter;

import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.CustomResponseMetaContextHolder;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.system.definition.SettingDefinition;
import com.mjusugangsincheonghelper.system.definition.SettingDefinition.PerformanceThresholds;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalMetaFilter extends OncePerRequestFilter {

	private final ClientInfoExtractor clientInfoExtractor;
	private final InstanceIdProvider instanceIdProvider;
	private final SystemConfigService systemConfigService;

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

		MDC.put("requestId", requestId);
		MDC.put("instanceId", instanceIdProvider.getInstanceId());

		response.setHeader("X-Request-Id", requestId);
		if (apiVersion != null) {
			response.setHeader("X-Api-Version", apiVersion);
		}

		try {
			filterChain.doFilter(request, response);
		} finally {
			long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
			logSlowRequest(request.getMethod(), request.getRequestURI(), durationMs);

			MDC.clear();
			CustomResponseMetaContextHolder.clear();
		}
	}

	private void logSlowRequest(String method, String path, long durationMs) {
		PerformanceThresholds thresholds = SettingDefinition.PERFORMANCE_THRESHOLDS.getFrom(systemConfigService);
		if (durationMs > thresholds.verySlowMs()) {
			log.error("Very slow request: {} {} took {}ms", method, path, durationMs);
		} else if (durationMs > thresholds.slowMs()) {
			log.warn("Slow request: {} {} took {}ms", method, path, durationMs);
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
