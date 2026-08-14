package com.mjusugangsincheonghelper.latency.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatencyDistributionResponse {

	private DistributionData median;
	private DistributionData worst;
	private DistributionData jitter;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DistributionData {
		private List<HistogramBucket> histogram;
		private SummaryData summary;
		private Double myValue;
		private Long myRank;
		private Long totalParticipants;
		private Double myPercentile;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class HistogramBucket {
		private Integer bucketStart;
		private Integer bucketEnd;
		private Long count;
		private Double percentage;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SummaryData {
		private Double averageMs;
		private Double p50Ms;
		private Double p90Ms;
	}
}
