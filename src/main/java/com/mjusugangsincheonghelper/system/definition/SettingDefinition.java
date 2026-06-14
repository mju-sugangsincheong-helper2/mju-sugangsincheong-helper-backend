package com.mjusugangsincheonghelper.system.definition;

import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.database.entity.SystemConfig.ConfigType;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettingDefinition {

	CURRENT_TERM(
			"current_term",
			ConfigType.STRING,
			"현재 학기 설정 (YYYY + 학기코드: 10=1학기, 15=여름학기, 20=2학기, 25=겨울학기)",
			"202510",
			raw -> new TermCode(raw),
			raw -> raw != null && raw.matches("^20\\d{2}(10|15|20|25)$")
	),

	NOTICES(
			"notices",
			ConfigType.JSON,
			"공지사항 목록",
			"[]",
			raw -> {
				ObjectMapper mapper = new ObjectMapper();
				return mapper.readTree(raw);
			},
			raw -> {
				try {
					new ObjectMapper().readTree(raw);
					return true;
				} catch (Exception e) {
					return false;
				}
			}
	),

	ANNOUNCEMENT(
			"announcement",
			ConfigType.STRING,
			"상단 배너 공지 텍스트",
			"",
			raw -> raw,
			raw -> true
	),

	EXPOSE_ERROR_DETAILS(
			"expose_error_details",
			ConfigType.BOOLEAN,
			"에러 응답에 원본 예외 상세 정보 포함 여부",
			"false",
			Boolean::parseBoolean,
			raw -> "true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)
	),

	PERFORMANCE_THRESHOLDS(
			"performance_thresholds",
			ConfigType.JSON,
			"성능 임계값 설정 (slow_ms, very_slow_ms)",
			"{\"slowMs\":1000,\"verySlowMs\":5000}",
			raw -> {
				try {
					return new ObjectMapper().readValue(raw, PerformanceThresholds.class);
				} catch (Exception e) {
					return new PerformanceThresholds(1000L, 5000L);
				}
			},
			raw -> {
				try {
					new ObjectMapper().readValue(raw, PerformanceThresholds.class);
					return true;
				} catch (Exception e) {
					return false;
				}
			}
	),

	JWT_EXPIRY_CONFIG(
			"jwt_expiry_config",
			ConfigType.JSON,
			"JWT 토큰 만료 시간 설정 (ms)",
			"{\"accessTokenExpiryMs\":3600000,\"refreshTokenExpiryMs\":604800000,\"mergeTicketExpiryMs\":300000}",
			raw -> {
				try {
					return new ObjectMapper().readValue(raw, JwtExpiryConfig.class);
				} catch (Exception e) {
					return new JwtExpiryConfig(3600000L, 604800000L, 300000L);
				}
			},
			raw -> {
				try {
					new ObjectMapper().readValue(raw, JwtExpiryConfig.class);
					return true;
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

	public record TermCode(int year, String semester) {
		public TermCode(String raw) {
			this(Integer.parseInt(raw.substring(0, 4)), raw.substring(4));
		}

		public String getDisplayName() {
			return switch (semester) {
				case "10" -> year + "년도 1학기";
				case "15" -> year + "년도 여름학기";
				case "20" -> year + "년도 2학기";
				case "25" -> year + "년도 겨울학기";
				default -> year + "년도 (알 수 없음)";
			};
		}
	}

	public record PerformanceThresholds(long slowMs, long verySlowMs) {}

	public record JwtExpiryConfig(long accessTokenExpiryMs, long refreshTokenExpiryMs, long mergeTicketExpiryMs) {}

}
