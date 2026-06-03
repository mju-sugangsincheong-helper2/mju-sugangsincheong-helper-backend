package com.mjusugangsincheonghelper.global.api.support;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class CustomResponseMetaContextHolder {

	private static final ThreadLocal<MetaContext> CONTEXT = new ThreadLocal<>();

	private CustomResponseMetaContextHolder() {
	}

	public static void initialize(String requestId, String apiVersion, String path, String method,
			String ipAddress, String userAgent, Instant startedAt) {
		CONTEXT.set(new MetaContext(requestId, apiVersion, path, method, ipAddress, userAgent, startedAt));
	}

	public static MetaContext get() {
		return CONTEXT.get();
	}

	public static void clear() {
		CONTEXT.remove();
	}

	@Getter
	@AllArgsConstructor
	public static class MetaContext {

		private final String requestId;
		private final String apiVersion;
		private final String path;
		private final String method;
		private final String ipAddress;
		private final String userAgent;
		private final Instant startedAt;
	}
}
