package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.system.dto.SystemConfigUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SystemConfigService 통합 테스트")
class SystemConfigServiceTest {

	@Autowired
	private SystemConfigService systemConfigService;

	@Autowired
	private com.mjusugangsincheonghelper.database.repository.SystemConfigRepository systemConfigRepository;

	@Test
	@DisplayName("getRaw는 DB에서 설정 값을 조회한다")
	void it_returns_config_value_from_db() {
		String configKey = "current_term";

		String term = systemConfigService.getRaw(configKey);

		assertThat(term).isNotNull();
	}

	@Test
	@DisplayName("update는 설정 값을 수정한다")
	void it_updates_config_value() {
		String configKey = "current_term";
		String originalValue = systemConfigService.getRaw(configKey);

		SystemConfigUpdateRequest request = new SystemConfigUpdateRequest("202620", "Test Description");
		systemConfigService.update(configKey, request);

		String updatedValue = systemConfigService.getRaw(configKey);
		assertThat(updatedValue).isEqualTo("202620");

		// Restore original value
		systemConfigRepository.findById(configKey).ifPresent(config -> {
			config.updateValue(originalValue, "현재 학기 설정");
			systemConfigRepository.save(config);
		});
	}
}
