package com.mjusugangsincheonghelper.global.api.support;

import com.mjusugangsincheonghelper.global.api.meta.ResponseMeta;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class MetaGenerator {

	private MetaGenerator() {
	}

	public static ResponseMeta generate() {
		Instant now = Instant.now();
		CustomResponseMetaContextHolder.MetaContext context = CustomResponseMetaContextHolder.get();

		if (context == null) {
			return ResponseMeta.builder()
					.requestId(UUID.randomUUID().toString())
					.timestamp(now)
					.durationMs(0)
					.build();
		}

		long durationMs = Duration.between(context.getStartedAt(), now).toMillis();

		return ResponseMeta.builder()
				.requestId(context.getRequestId())
				.apiVersion(context.getApiVersion())
				.path(context.getPath())
				.method(context.getMethod())
				.timestamp(now)
				.durationMs(durationMs)
				.ipAddress(context.getIpAddress())
				.userAgent(context.getUserAgent())
				.build();
	}
}
