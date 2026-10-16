package com.mjusugangsincheonghelper.global.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@link HttpServerRequestEvent} 기록 필터. URL 기준 콜스택 측정의 서버측 절반이다.
 *
 * <p>동작 원칙: JFR 레코딩이 꺼져 있으면 헤더 읽기도 없이 그대로 통과한다.
 * 켜는 주체는 코드가 아니라 {@code jcmd <pid> JFR.start}이므로 평소엔 있는지도 모른다.</p>
 *
 * <p>순서: GlobalMetaFilter(100) 안쪽. X-Request-Id를 재사용하기 위함.
 * 없어도 UUID fallback이라 독립 동작한다.</p>
 */
@Profile("dev")
@Component
@Order(101)
public class JfrWindowFilter extends OncePerRequestFilter {

	// ===== 설정 (새 프로젝트에서 손댈 곳, 여기만) =====
	private static final String TAG_HEADER = "X-JFR-TAG";
	private static final int QUERY_CAP = 512;
	private static final int TAG_CAP = 256;
	private static final List<String> INCLUDE_URLS = List.of("/api/");
	private static final List<String> EXCLUDE_URLS = List.of("/actuator/", "/swagger", "/v3/api-docs");
	// =================================================

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !recordable(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		HttpServerRequestEvent event = new HttpServerRequestEvent();
		if (!event.shouldCommit()) {
			filterChain.doFilter(request, response);
			return;
		}
		event.method = request.getMethod();
		event.route = normalizeRoute(request.getRequestURI());
		event.query = cap(request.getQueryString(), QUERY_CAP);
		event.jfrTag = cap(request.getHeader(TAG_HEADER), TAG_CAP);
		event.requestId = resolveRequestId(response);
		event.begin();
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			event.status = response.getStatus();
			event.commit();
		}
	}

	static boolean recordable(String uri) {
		if (uri == null) {
			return false;
		}
		if (EXCLUDE_URLS.stream().anyMatch(uri::startsWith)) {
			return false;
		}
		return INCLUDE_URLS.isEmpty() || INCLUDE_URLS.stream().anyMatch(uri::startsWith);
	}

	static String normalizeRoute(String uri) {
		if (uri == null) {
			return "";
		}
		String[] segments = uri.split("/", -1);
		for (int i = 0; i < segments.length; i++) {
			if (segments[i].matches("\\d+")) {
				segments[i] = "{id}";
			}
		}
		return String.join("/", segments);
	}

	static String cap(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	private static String resolveRequestId(HttpServletResponse response) {
		String header = response.getHeader("X-Request-Id");
		if (header != null && !header.isEmpty()) {
			return header;
		}
		String mdc = MDC.get("requestId");
		if (mdc != null && !mdc.isEmpty()) {
			return mdc;
		}
		return UUID.randomUUID().toString();
	}
}
