package com.mjusugangsincheonghelper.latency.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LatencyMyRecordResponse {

	private Long id;
	private Double medianMs;
	private Double maxMs;
	private Double minMs;
	private Double stdDevMs;
	private Integer sampleCount;
	private List<Double> samples;
	private Instant createdAt;
}
