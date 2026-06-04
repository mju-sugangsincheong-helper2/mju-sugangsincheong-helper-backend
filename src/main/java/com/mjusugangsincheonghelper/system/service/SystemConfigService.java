package com.mjusugangsincheonghelper.system.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.database.entity.SystemConfig.ConfigType;
import com.mjusugangsincheonghelper.database.entity.SystemConfig;
import com.mjusugangsincheonghelper.database.repository.SystemConfigRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.system.dto.SystemConfigResponse;
import com.mjusugangsincheonghelper.system.dto.SystemConfigUpdateRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@DependsOn("entityManagerFactory")
public class SystemConfigService {

	public static final String EXPOSE_ERROR_DETAILS_KEY = "expose_error_details";
	public static final String PERFORMANCE_THRESHOLDS_KEY = "performance_thresholds";
	private static final String DEFAULT_THRESHOLDS_JSON = "{\"slow_ms\":1000,\"very_slow_ms\":5000}";

	private final SystemConfigRepository repository;
	private final ObjectMapper objectMapper;

	@PostConstruct
	public void initDefaultConfigs() {
		if (!repository.existsById(EXPOSE_ERROR_DETAILS_KEY)) {
			repository.save(SystemConfig.builder()
					.configKey(EXPOSE_ERROR_DETAILS_KEY)
					.configValue("true")
					.configType(ConfigType.BOOLEAN)
					.description("에러 응답에 원본 예외 상세 정보 포함 여부")
					.build());
			log.info("Initialized default system config: {}={}", EXPOSE_ERROR_DETAILS_KEY, "true");
		}
		if (!repository.existsById(PERFORMANCE_THRESHOLDS_KEY)) {
			repository.save(SystemConfig.builder()
					.configKey(PERFORMANCE_THRESHOLDS_KEY)
					.configValue(DEFAULT_THRESHOLDS_JSON)
					.configType(ConfigType.JSON)
					.description("성능 임계값 설정 (slow_ms, very_slow_ms)")
					.build());
			log.info("Initialized default system config: {}={}", PERFORMANCE_THRESHOLDS_KEY, DEFAULT_THRESHOLDS_JSON);
		}
	}

	@Transactional(readOnly = true)
	public SystemConfigResponse find(String configKey) {
		SystemConfig config = repository.findById(configKey)
				.orElseThrow(() -> new BaseException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND));
		return SystemConfigResponse.from(config);
	}

	@Transactional
	@CacheEvict(value = "system_config", key = "#configKey")
	public SystemConfigResponse update(String configKey, SystemConfigUpdateRequest request) {
		SystemConfig config = repository.findById(configKey)
				.orElseThrow(() -> new BaseException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND));

		config.updateValue(request.getConfigValue(), request.getDescription());
		return SystemConfigResponse.from(config);
	}

	@Cacheable(value = "system_config", key = "#configKey")
	public boolean getBoolean(String configKey, boolean defaultValue) {
		return repository.findById(configKey)
				.map(c -> Boolean.parseBoolean(c.getConfigValue()))
				.orElse(defaultValue);
	}

	public PerformanceThresholds getPerformanceThresholds() {
		return repository.findById(PERFORMANCE_THRESHOLDS_KEY)
				.map(c -> {
					try {
						JsonNode node = objectMapper.readTree(c.getConfigValue());
						return new PerformanceThresholds(
								node.path("slow_ms").asLong(1000),
								node.path("very_slow_ms").asLong(5000)
						);
					} catch (Exception e) {
						return new PerformanceThresholds(1000, 5000);
					}
				})
				.orElse(new PerformanceThresholds(1000, 5000));
	}

	public record PerformanceThresholds(long slowMs, long verySlowMs) {
	}
}
