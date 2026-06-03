package com.mjusugangsincheonghelper.global.config;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@EnableCaching
@Configuration
public class RedisConfig {

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(jsonSerializer);
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(jsonSerializer);
		return template;
	}

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(Duration.ofHours(24))
				.serializeValuesWith(
						RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json())
				);

		return RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(config)
				.build();
	}

	@Bean
	public CacheErrorHandler cacheErrorHandler() {
		return new CacheErrorHandler() {
			@Override
			public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
				log.warn("Cache get error on {} for key {}: {}", cache.getName(), key, exception.getMessage());
			}

			@Override
			public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
				log.warn("Cache put error on {} for key {}: {}", cache.getName(), key, exception.getMessage());
			}

			@Override
			public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
				log.warn("Cache evict error on {} for key {}: {}", cache.getName(), key, exception.getMessage());
			}

			@Override
			public void handleCacheClearError(RuntimeException exception, Cache cache) {
				log.warn("Cache clear error on {}: {}", cache.getName(), exception.getMessage());
			}
		};
	}
}
