package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.global.config.RedisConfig;
import com.mjusugangsincheonghelper.system.dto.SystemConfigUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SystemConfigService 캐싱 및 동기화 테스트")
class SystemConfigServiceTest {

	@Autowired
	private SystemConfigService systemConfigService;

	@Autowired
	@Qualifier("caffeineCacheManager")
	private CacheManager caffeineCacheManager;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Autowired
	private com.mjusugangsincheonghelper.database.repository.SystemConfigRepository systemConfigRepository;

	private Cache cache;

	@BeforeEach
	void setUp() {
		// Clean database state
		systemConfigRepository.findById("current_term").ifPresent(config -> {
			config.updateValue("202510", "현재 학기 설정");
			systemConfigRepository.save(config);
		});

		cache = caffeineCacheManager.getCache("system-config");
		if (cache != null) {
			cache.clear();
		}
	}

	@Test
	@DisplayName("getRaw 호출 시 결과가 로컬 Caffeine 캐시에 저장된다")
	void it_caches_getRaw_in_caffeine() {
		// Given
		String configKey = "current_term";

		// When
		String term = systemConfigService.getRaw(configKey);

		// Then
		assertThat(cache).isNotNull();
		Cache.ValueWrapper wrapper = cache.get(configKey + ":cache");
		assertThat(wrapper).isNotNull();
		assertThat(wrapper.get()).isEqualTo(term);
	}

	@Test
	@DisplayName("update 호출 시 로컬 Caffeine 캐시가 비워진다")
	void it_evicts_cache_on_update() {
		// Given
		String configKey = "current_term";
		systemConfigService.getRaw(configKey); // cache it
		assertThat(cache.get(configKey + ":cache")).isNotNull();

		SystemConfigUpdateRequest request = new SystemConfigUpdateRequest("202620", "Test Description");

		// When
		systemConfigService.update(configKey, request);

		// Then
		Cache.ValueWrapper wrapper = cache.get(configKey + ":cache");
		assertThat(wrapper).isNull();
	}

	@Test
	@DisplayName("Redis Pub/Sub을 통해 전송된 다른 노드의 무효화 메시지를 받으면 캐시가 무효화된다")
	void it_evicts_local_cache_on_redis_pubsub_message() throws InterruptedException {
		// Given
		String configKey = "current_term";
		systemConfigService.getRaw(configKey); // cache it
		assertThat(cache.get(configKey + ":cache")).isNotNull();

		String otherInstanceId = "some-other-instance-id";
		String payload = otherInstanceId + ":" + configKey + ":cache";

		// When: 다른 WAS 노드에서 무효화 메시지를 발행한 것으로 시뮬레이션
		redisTemplate.convertAndSend(RedisConfig.SYSTEM_CONFIG_EVICT_TOPIC, payload);

		// Then: 비동기 Pub/Sub 전파 대기 (최대 1초 대기하며 검증)
		boolean evicted = false;
		for (int i = 0; i < 10; i++) {
			if (cache.get(configKey + ":cache") == null) {
				evicted = true;
				break;
			}
			Thread.sleep(100);
		}
		assertThat(evicted).isTrue();
	}
}
