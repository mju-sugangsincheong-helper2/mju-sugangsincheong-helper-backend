package com.mjusugangsincheonghelper.system.dto;

import com.mjusugangsincheonghelper.database.entity.SystemConfig;
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

	public static SystemConfigResponse from(SystemConfig entity) {
		return SystemConfigResponse.builder()
				.configKey(entity.getConfigKey())
				.configValue(entity.getConfigValue())
				.configType(entity.getConfigType().name())
				.description(entity.getDescription())
				.updatedAt(entity.getUpdatedAt())
				.build();
	}
}
