package com.mjusugangsincheonghelper.global.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

	public static final String SYSTEM_CONFIG = "system-config";
	public static final String EXCHANGE_FEED = "exchange-feed";
	public static final String EXCHANGE_MAIN = "exchange-main";
	public static final String EXCHANGE_ROOM_META = "exchange-room-meta";
	public static final String SINGLEGAME_RANK = "singlegame-rank";
	public static final String SINGLEGAME_STATS = "singlegame-stats";
	public static final String MULTIGAME_RANK = "multigame-rank";
	public static final String LATENCY_DISTRIBUTION = "latency-distribution";

	private Duration defaultTtl = Duration.ofHours(24);
	private Map<String, Duration> ttls = new HashMap<>();

	public Duration getTtl(String cacheName) {
		return ttls.getOrDefault(cacheName, defaultTtl);
	}
}
