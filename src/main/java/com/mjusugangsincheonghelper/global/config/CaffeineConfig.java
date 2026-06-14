package com.mjusugangsincheonghelper.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaffeineConfig {

	record TimestampedValue(Object value, int ttlSeconds) {}

	@Bean(name = "caffeineCacheManager")
	public CacheManager caffeineCacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager("system-config");
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.expireAfterWrite(24, TimeUnit.HOURS)
				.maximumSize(100));
		return cacheManager;
	}

	@Bean
	public com.github.benmanes.caffeine.cache.Cache<String, TimestampedValue> caffeineCache() {
		return Caffeine.newBuilder()
				.maximumSize(2000)
				.expireAfter(new Expiry<String, TimestampedValue>() {
					@Override
					public long expireAfterCreate(String key, TimestampedValue value, long currentTime) {
						return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
					}

					@Override
					public long expireAfterUpdate(String key, TimestampedValue value,
							long currentTime, long currentDuration) {
						return currentDuration;
					}

					@Override
					public long expireAfterRead(String key, TimestampedValue value,
							long currentTime, long currentDuration) {
						return currentDuration;
					}
				})
				.build();
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface CaffeineCache {
		String key();
		int ttl() default 10;
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface CaffeineCacheEvict {
		String key();
	}

	@Aspect
	static class CaffeineAspect {

		private final com.github.benmanes.caffeine.cache.Cache<String, TimestampedValue> cache;

		CaffeineAspect(com.github.benmanes.caffeine.cache.Cache<String, TimestampedValue> cache) {
			this.cache = cache;
		}

		@Around("@annotation(cacheAnn)")
		public Object aroundCache(ProceedingJoinPoint joinPoint, CaffeineCache cacheAnn) throws Throwable {
			String key = cacheAnn.key();

			TimestampedValue cached = cache.getIfPresent(key);
			if (cached != null) {
				return cached.value();
			}

			Object result = joinPoint.proceed();
			cache.put(key, new TimestampedValue(result, cacheAnn.ttl()));
			return result;
		}

		@Around("@annotation(evictAnn)")
		public Object aroundEvict(ProceedingJoinPoint joinPoint, CaffeineCacheEvict evictAnn) throws Throwable {
			cache.invalidate(evictAnn.key());
			return joinPoint.proceed();
		}
	}
}
