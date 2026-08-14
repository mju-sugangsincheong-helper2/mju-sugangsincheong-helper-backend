package com.mjusugangsincheonghelper.latency.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Latency 도메인 설정. yml의 {@code app.latency} 하위 항목으로 바인딩된다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.latency")
public class LatencyProperties {

	/** 최소 샘플 값 (ms) */
	private int sampleMinMs = 1;

	/** 최대 샘플 값 (ms) */
	private int sampleMaxMs = 30000;

	/** 히스토그램 캐시 TTL */
	private Duration distributionCacheTtl = Duration.ofMinutes(5);

	/** Median/Worst 히스토그램 버킷 간격 (ms) */
	private int histogramBucketSizeMs = 2;

	/** Jitter (StdDev) 히스토그램 버킷 간격 (ms) */
	private int jitterBucketSizeMs = 1;

	/** 히스토그램 최대 범위 (ms) */
	private int histogramMaxMs = 500;

	/** Jitter 히스토그램 최대 범위 (ms) */
	private int jitterMaxMs = 100;
}
