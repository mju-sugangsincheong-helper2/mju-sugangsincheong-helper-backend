package com.mjusugangsincheonghelper.global.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

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
