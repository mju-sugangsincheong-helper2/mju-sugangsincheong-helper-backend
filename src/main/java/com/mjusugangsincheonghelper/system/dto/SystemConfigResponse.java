package com.mjusugangsincheonghelper.system.dto;

import com.mjusugangsincheonghelper.database.entity.SystemConfig;
import com.mjusugangsincheonghelper.system.definition.SettingDefinition;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SystemConfigResponse {

	private final String configKey;
	private final String configValue;
	private final String configType;
	private final String description;
	private final Instant updatedAt;
	private final String defaultValue;
	private final Object parsedValue;

	public static SystemConfigResponse from(SystemConfig entity) {
		SettingDefinition def = SettingDefinition.findByKey(entity.getConfigKey());
		return SystemConfigResponse.builder()
				.configKey(entity.getConfigKey())
				.configValue(entity.getConfigValue())
				.configType(entity.getConfigType().name())
				.description(entity.getDescription())
				.updatedAt(entity.getUpdatedAt())
				.defaultValue(def != null ? def.getDefaultValue() : null)
				.parsedValue(def != null ? def.parse(entity.getConfigValue()) : null)
				.build();
	}
}
