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

	public Duration getTtl(String cacheName) {
		return ttls.getOrDefault(cacheName, defaultTtl);
	}
}
