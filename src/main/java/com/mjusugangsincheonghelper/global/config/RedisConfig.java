package com.mjusugangsincheonghelper.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
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
		ObjectMapper redisMapper = new ObjectMapper();
		redisMapper.registerModule(new JavaTimeModule());
		redisMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		redisMapper.activateDefaultTyping(
				redisMapper.getPolymorphicTypeValidator(),
				DefaultTyping.NON_FINAL
		);
		return new GenericJackson2JsonRedisSerializer(redisMapper);
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
