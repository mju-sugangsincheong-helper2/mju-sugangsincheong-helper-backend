package com.mjusugangsincheonghelper.system.dto;

import com.mjusugangsincheonghelper.database.entity.SystemConfig;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigResponse {

	private String configKey;
	private String configValue;
	private String configType;
	private String description;
	private Instant updatedAt;

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
