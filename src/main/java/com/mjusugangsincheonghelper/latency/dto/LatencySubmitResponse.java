package com.mjusugangsincheonghelper.latency.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class LatencySubmitResponse {

	private RecordInfo record;
	private LatencyDistributionResponse distribution;

	@Getter
	@Builder
	@AllArgsConstructor
	public static class RecordInfo {
		private Long id;
		private Instant createdAt;
	}
}
