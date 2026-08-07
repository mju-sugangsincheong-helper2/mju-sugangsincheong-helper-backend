package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.database.entity.SystemConfig;
import com.mjusugangsincheonghelper.database.repository.SystemConfigRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import com.mjusugangsincheonghelper.system.definition.SettingDefinition;
import com.mjusugangsincheonghelper.system.dto.SystemConfigResponse;
import com.mjusugangsincheonghelper.system.dto.SystemConfigUpdateRequest;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@DependsOn("entityManagerFactory")
public class SystemConfigService {

	private final SystemConfigRepository repository;
	private final CacheManager cacheManager;

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
				log.info("Initialized default system config. key={}, defaultValue={}", def.getKey(), def.getDefaultValue());
			}
		}

		// 공지사항이 전용 notice 테이블로 이전됨. 기존 JSON 기반 notices 키가 남아 있으면 제거한다.
		if (repository.existsById("notices")) {
			repository.deleteById("notices");
			log.info("Removed obsolete system config key. key=notices");
		}
	}

	@Cacheable(value = CacheProperties.SYSTEM_CONFIG, key = "'current_term:cache'")
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

	@Transactional
	public SystemConfigResponse update(String configKey, SystemConfigUpdateRequest request) {
		SystemConfig config = repository.findById(configKey)
				.orElseGet(() -> {
					SettingDefinition def = SettingDefinition.findByKey(configKey);
					if (def == null) {
						throw new BaseException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND);
					}
					return repository.save(SystemConfig.builder()
							.configKey(def.getKey())
							.configValue(def.getDefaultValue())
							.configType(def.getType())
							.description(def.getDescription())
							.build());
				});

		String previousValue = config.getConfigValue();
		config.updateValue(request.getConfigValue(), request.getDescription());

		var cache = cacheManager.getCache(CacheProperties.SYSTEM_CONFIG);
		if (cache != null) {
			cache.evict(configKey + ":cache");
		}

		log.info("Updated system config. key={}, from={}, to={}", configKey, previousValue, config.getConfigValue());

		return SystemConfigResponse.from(config);
	}

	@Cacheable(value = CacheProperties.SYSTEM_CONFIG, key = "#configKey + ':cache'")
	public String getRaw(String configKey) {
		return repository.findById(configKey)
				.map(SystemConfig::getConfigValue)
				.orElseGet(() -> {
					SettingDefinition def = SettingDefinition.findByKey(configKey);
					return def != null ? def.getDefaultValue() : null;
				});
	}
}
