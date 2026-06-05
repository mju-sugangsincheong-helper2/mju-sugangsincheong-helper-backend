package com.mjusugangsincheonghelper.system.definition;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.database.entity.SystemConfig;
import com.mjusugangsincheonghelper.database.entity.SystemConfig.ConfigType;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettingDefinition {

	EXPOSE_ERROR_DETAILS(
			"expose_error_details",
			ConfigType.BOOLEAN,
			"에러 응답에 원본 예외 상세 정보 포함 여부",
			"true",
			raw -> Boolean.parseBoolean(raw),
			raw -> "true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)
	),

	PERFORMANCE_THRESHOLDS(
			"performance_thresholds",
			ConfigType.JSON,
			"성능 임계값 설정 (slow_ms, very_slow_ms)",
			"{\"slow_ms\":1000,\"very_slow_ms\":5000}",
			raw -> {
				ObjectMapper mapper = new ObjectMapper();
				JsonNode node = mapper.readTree(raw);
				return new PerformanceThresholds(
						node.path("slow_ms").asLong(1000),
						node.path("very_slow_ms").asLong(5000)
				);
			},
			raw -> {
				try {
					ObjectMapper mapper = new ObjectMapper();
					JsonNode node = mapper.readTree(raw);
					return node.has("slow_ms") && node.has("very_slow_ms");
				} catch (Exception e) {
					return false;
				}
			}
	);

	private final String key;
	private final ConfigType type;
	private final String description;
	private final String defaultValue;
	private final Function<String, Object> parser;
	private final Function<String, Boolean> validator;

	public Object parse(String rawValue) {
		if (rawValue == null) {
			return parser.apply(defaultValue);
		}
		try {
			return parser.apply(rawValue);
		} catch (Exception e) {
			return parser.apply(defaultValue);
		}
	}

	public boolean validate(String rawValue) {
		return validator.apply(rawValue);
	}

	@SuppressWarnings("unchecked")
	public <T> T getFrom(SystemConfigService service) {
		String raw = service.getRaw(key);
		if (raw == null) {
			return (T) parse(defaultValue);
		}
		return (T) parse(raw);
	}

	public static SettingDefinition findByKey(String key) {
		for (SettingDefinition def : values()) {
			if (def.key.equals(key)) {
				return def;
			}
		}
		return null;
	}

	public record PerformanceThresholds(long slowMs, long verySlowMs) {
	}
}
