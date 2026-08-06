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
	public static final String EXCHANGE_USER_INTENTS = "exchange-user-intents";
	public static final String EXCHANGE_ROOM_META = "exchange-room-meta";
	public static final String SINGLEGAME_RANK = "singlegame-rank";
	public static final String SINGLEGAME_RECORDS = "singlegame-records";
	public static final String SINGLEGAME_ANALYSIS = "singlegame-analysis";
	public static final String MULTIGAME_RANK = "multigame-rank";

	private Duration defaultTtl = Duration.ofHours(24);
	private Map<String, Duration> ttls = new HashMap<>();

	/**
	 * evict 직후 스케줄러로 한 번 더 삭제하는 더블 evict 지연 시간.
	 * 캐시 쓰기 경합(read-through 재캐시 vs evict 순서 역전)을 막기 위한 안전장치.
	 */
	private Duration doubleEvictDelay = Duration.ofSeconds(2);

	public Duration getTtl(String cacheName) {
		return ttls.getOrDefault(cacheName, defaultTtl);
	}
}
