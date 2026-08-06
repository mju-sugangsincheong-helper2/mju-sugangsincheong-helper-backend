package com.mjusugangsincheonghelper.global.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@EnableCaching
@Configuration
@RequiredArgsConstructor
public class RedisConfig implements CachingConfigurer {

	private final CacheProperties cacheProperties;

	@Bean
	public RedisSerializer<Object> redisSerializer() {
		return GenericJacksonJsonRedisSerializer.builder()
				.enableUnsafeDefaultTyping()
				.customize(builder -> builder.findAndAddModules())
				// Jackson 3 기반 SDR 4.x는 루트 값(특히 List)에 타입 정보를 남기지 않아 재역직렬화가 깨진다.
				// 루트를 Object로 선언해 쓰면 타입 id가 항상 기록되어 빈/비어있지 않은 List도 캐시된다.
				.writer((mapper, value) -> mapper.writerFor(Object.class).writeValueAsBytes(value))
				.build();
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, RedisSerializer<Object> redisSerializer) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(redisSerializer);
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(redisSerializer);
		return template;
	}

	@Primary
	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, RedisSerializer<Object> redisSerializer) {
		Duration defaultTtl = cacheProperties.getDefaultTtl();
		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(defaultTtl)
				.serializeValuesWith(
						RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer)
				);

		Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
		for (Map.Entry<String, Duration> entry : cacheProperties.getTtls().entrySet()) {
			cacheConfigurations.put(entry.getKey(), defaultConfig.entryTtl(entry.getValue()));
		}

		return RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(defaultConfig)
				.withInitialCacheConfigurations(cacheConfigurations)
				.build();
	}

	@Override
	public CacheErrorHandler errorHandler() {
		return new CacheErrorHandler() {
			@Override
			public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
				log.warn("Cache get error. cache={}, key={}, message={}", cache.getName(), key, exception.getMessage());
			}

			@Override
			public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
				log.warn("Cache put error. cache={}, key={}, message={}", cache.getName(), key, exception.getMessage());
			}

			@Override
			public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
				log.warn("Cache evict error. cache={}, key={}, message={}", cache.getName(), key, exception.getMessage());
			}

			@Override
			public void handleCacheClearError(RuntimeException exception, Cache cache) {
				log.warn("Cache clear error. cache={}, message={}", cache.getName(), exception.getMessage());
			}
		};
	}
}
