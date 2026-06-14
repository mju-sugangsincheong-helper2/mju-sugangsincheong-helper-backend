package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.database.entity.SystemConfig;
import com.mjusugangsincheonghelper.database.repository.SystemConfigRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.definition.SettingDefinition;
import com.mjusugangsincheonghelper.system.dto.SystemConfigResponse;
import com.mjusugangsincheonghelper.system.dto.SystemConfigUpdateRequest;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.mjusugangsincheonghelper.global.config.RedisConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
@DependsOn("entityManagerFactory")
public class SystemConfigService {

	private final SystemConfigRepository repository;
	private final RedisTemplate<String, Object> redisTemplate;
	private final String instanceId;

	@Value("${app.expose-error-details:false}")
	private boolean defaultExposeErrorDetails;

	@Value("${app.performance.slow-ms:1000}")
	private long defaultSlowMs;

	@Value("${app.performance.very-slow-ms:5000}")
	private long defaultVerySlowMs;

	@Value("${app.jwt.access-token-expiry-ms:3600000}")
	private long defaultAccessTokenExpiryMs;

	@Value("${app.jwt.refresh-token-expiry-ms:604800000}")
	private long defaultRefreshTokenExpiryMs;

	@Value("${app.jwt.merge-ticket-expiry-ms:300000}")
	private long defaultMergeTicketExpiryMs;

	@PostConstruct
	public void initDefaultConfigs() {
		for (SettingDefinition def : SettingDefinition.values()) {
			if (!repository.existsById(def.getKey())) {
				String defaultValue = def.getDefaultValue();
				if (def == SettingDefinition.EXPOSE_ERROR_DETAILS) {
					defaultValue = String.valueOf(defaultExposeErrorDetails);
				} else if (def == SettingDefinition.PERFORMANCE_THRESHOLDS) {
					defaultValue = String.format("{\"slowMs\":%d,\"verySlowMs\":%d}", defaultSlowMs, defaultVerySlowMs);
				} else if (def == SettingDefinition.JWT_EXPIRY_CONFIG) {
					defaultValue = String.format("{\"accessTokenExpiryMs\":%d,\"refreshTokenExpiryMs\":%d,\"mergeTicketExpiryMs\":%d}",
							defaultAccessTokenExpiryMs, defaultRefreshTokenExpiryMs, defaultMergeTicketExpiryMs);
				}
				repository.save(SystemConfig.builder()
						.configKey(def.getKey())
						.configValue(defaultValue)
						.configType(def.getType())
						.description(def.getDescription())
						.build());
				log.info("Initialized default system config: {}={}", def.getKey(), defaultValue);
			}
		}
	}

	@Cacheable(value = "system-config", key = "'current_term:' + 'cache'", cacheManager = "caffeineCacheManager")
	public String getCurrentTerm() {
		return getRaw("current_term");
	}

	@Transactional(readOnly = true)
	public List<SystemConfigResponse> findAll() {
		return repository.findAll().stream()
				.map(SystemConfigResponse::from)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public SystemConfigResponse find(String configKey) {
		SystemConfig config = repository.findById(configKey)
				.orElseThrow(() -> new BaseException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND));
		return SystemConfigResponse.from(config);
	}

	@CacheEvict(value = "system-config", key = "#configKey + ':cache'", cacheManager = "caffeineCacheManager")
	@Transactional
	public SystemConfigResponse update(String configKey, SystemConfigUpdateRequest request) {
		SystemConfig config = repository.findById(configKey)
				.orElseThrow(() -> new BaseException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND));

		config.updateValue(request.getConfigValue(), request.getDescription());

		publishEviction(configKey + ":cache");

		return SystemConfigResponse.from(config);
	}

	private void publishEviction(String cacheKey) {
		String payload = instanceId + ":" + cacheKey;
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					redisTemplate.convertAndSend(RedisConfig.SYSTEM_CONFIG_EVICT_TOPIC, payload);
				}
			});
		} else {
			redisTemplate.convertAndSend(RedisConfig.SYSTEM_CONFIG_EVICT_TOPIC, payload);
		}
	}

	@Cacheable(value = "system-config", key = "#configKey + ':cache'", cacheManager = "caffeineCacheManager")
	public String getRaw(String configKey) {
		return repository.findById(configKey)
				.map(SystemConfig::getConfigValue)
				.orElseGet(() -> {
					SettingDefinition def = SettingDefinition.findByKey(configKey);
					return def != null ? def.getDefaultValue() : null;
				});
	}
}
