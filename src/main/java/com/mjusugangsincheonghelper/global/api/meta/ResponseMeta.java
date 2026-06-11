package com.mjusugangsincheonghelper.global.api.meta;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ResponseMeta {

	private final String requestId;
	private final String apiVersion;
	private final String path;
	private final String method;
	private final Instant timestamp;
	private final long durationMs;
	private final String ipAddress;
	private final String userAgent;
}
