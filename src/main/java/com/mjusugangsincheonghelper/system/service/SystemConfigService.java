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
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@DependsOn("entityManagerFactory")
public class SystemConfigService {

	private final SystemConfigRepository repository;

	@PostConstruct
	public void initDefaultConfigs() {
		for (SettingDefinition def : SettingDefinition.values()) {
			if (!repository.existsById(def.getKey())) {
				repository.save(SystemConfig.builder()
						.configKey(def.getKey())
						.configValue(def.getDefaultValue())
						.configType(def.getType())
						.description(def.getDescription())
						.build());
				log.info("Initialized default system config: {}={}", def.getKey(), def.getDefaultValue());
			}
		}
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

	@Transactional
	public SystemConfigResponse update(String configKey, SystemConfigUpdateRequest request) {
		SystemConfig config = repository.findById(configKey)
				.orElseThrow(() -> new BaseException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND));

		config.updateValue(request.getConfigValue(), request.getDescription());
		return SystemConfigResponse.from(config);
	}

	public String getRaw(String configKey) {
		return repository.findById(configKey)
				.map(SystemConfig::getConfigValue)
				.orElseGet(() -> {
					SettingDefinition def = SettingDefinition.findByKey(configKey);
					return def != null ? def.getDefaultValue() : null;
				});
	}
}
