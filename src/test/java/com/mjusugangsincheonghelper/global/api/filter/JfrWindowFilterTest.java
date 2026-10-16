package com.mjusugangsincheonghelper.global.api.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import java.nio.file.Path;
import java.util.List;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 스프링 컨텍스트 없이 필터 단독 검증. dev 프로파일·DB 불필요.
 */
class JfrWindowFilterTest {

	@TempDir
	Path tempDir;

	private final JfrWindowFilter filter = new JfrWindowFilter();

	private static MockHttpServletRequest request(String method, String uri) {
		MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
		req.setQueryString("totalCourses=6");
		req.addHeader("X-JFR-TAG", "exp01/006-30m");
		return req;
	}

	@Test
	void recordsEventWithAllFieldsWhenRecording(@TempDir Path dir) throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();
		res.setHeader("X-Request-Id", "req-123");
		FilterChain chain = (rq, rs) -> {
		};
		Path dump = dir.resolve("t.jfr");

		try (Recording recording = new Recording()) {
			recording.enable("http.server.request");
			recording.start();
			filter.doFilter(request("GET", "/api/1/singlegame/analysis/api"), res, chain);
			recording.stop();
			recording.dump(dump);
		}

		List<RecordedEvent> events = new java.util.ArrayList<>();
		try (RecordingFile file = new RecordingFile(dump)) {
			while (file.hasMoreEvents()) {
				RecordedEvent candidate = file.readEvent();
			if ("http.server.request".equals(candidate.getEventType().getName())) {
				events.add(candidate);
			}
		}
		}
		assertEquals(1, events.size());
		RecordedEvent event = events.get(0);
		assertEquals("GET", event.getString("method"));
		assertEquals("/api/{id}/singlegame/analysis/api", event.getString("route"));
		assertEquals("totalCourses=6", event.getString("query"));
		assertEquals("exp01/006-30m", event.getString("jfrTag"));
		assertEquals("req-123", event.getString("requestId"));
		assertEquals(200, event.getInt("status"));
	}

	@Test
	void passesThroughWithoutRecording() throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();
		// 레코딩 OFF: 예외 없이 체인만 타면 됨
		filter.doFilter(request("GET", "/api/1/x"), res, (rq, rs) -> {
		});
	}

	@Test
	void skipsExcludedUrls() {
		assertFalse(JfrWindowFilter.recordable("/actuator/health"));
		assertTrue(JfrWindowFilter.recordable("/api/1/x"));
		assertFalse(JfrWindowFilter.recordable("/other"));
	}

	@Test
	void normalizesNumericIds() {
		assertEquals("/api/{id}/users/{id}/games", JfrWindowFilter.normalizeRoute("/api/1/users/123/games"));
	}
}
