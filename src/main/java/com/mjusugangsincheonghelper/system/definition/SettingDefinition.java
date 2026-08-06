package com.mjusugangsincheonghelper.system.definition;

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
			"202620",
			raw -> new TermCode(raw),
			raw -> raw != null && raw.matches("^20\\d{2}(10|15|20|25)$")
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

}
