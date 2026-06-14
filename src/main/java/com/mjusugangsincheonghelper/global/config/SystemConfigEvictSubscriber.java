package com.mjusugangsincheonghelper.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SystemConfigEvictSubscriber {

	private final CacheManager caffeineCacheManager;
	private final String instanceId;

	public SystemConfigEvictSubscriber(
			@Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
			String instanceId) {
		this.caffeineCacheManager = caffeineCacheManager;
		this.instanceId = instanceId;
	}

	public void receiveEviction(String message) {
		try {
			int delimiterIdx = message.indexOf(':');
			if (delimiterIdx == -1) {
				return;
			}

			String senderId = message.substring(0, delimiterIdx);
			String cacheKey = message.substring(delimiterIdx + 1);

			if (instanceId.equals(senderId)) {
				return;
			}

			Cache cache = caffeineCacheManager.getCache("system-config");
			if (cache != null) {
				cache.evict(cacheKey);
				log.info("Evicted local JVM system-config cache for key='{}' due to pub/sub broadcast", cacheKey);
			}
		} catch (Exception e) {
			log.error("Failed to process system-config eviction broadcast message", e);
		}
	}
}
