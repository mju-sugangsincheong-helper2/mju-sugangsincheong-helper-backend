package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.database.entity.ConfigType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

	public static final String EXPOSE_ERROR_DETAILS_KEY = "expose_error_details";

	private final SystemConfigRepository repository;

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
}
