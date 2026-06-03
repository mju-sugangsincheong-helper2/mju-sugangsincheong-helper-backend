package com.mjusugangsincheonghelper.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.database.entity.ExampleEntity;
import com.mjusugangsincheonghelper.database.repository.ExampleRepository;
import com.mjusugangsincheonghelper.example.dto.ExampleCreateRequest;
import com.mjusugangsincheonghelper.example.dto.ExampleUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Example 통합 테스트")
class ExampleIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private ExampleRepository exampleRepository;

	@BeforeEach
	void setUp() {
		exampleRepository.deleteAll();
	}

	@Nested
	@DisplayName("생성 → 조회 흐름은")
	class Describe_createAndFind {

		@Test
		@DisplayName("전체 흐름이 정상 동작한다")
		void it_works_end_to_end() throws Exception {
			ExampleCreateRequest createRequest = ExampleCreateRequest.builder()
					.title("Integration Test")
					.content("Test Content")
					.build();

			String createResponse = mockMvc.perform(post("/api/v1/example")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.title").value("Integration Test"))
					.andExpect(jsonPath("$.meta.requestId").exists())
					.andReturn()
					.getResponse()
					.getContentAsString();

			Long createdId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

			mockMvc.perform(get("/api/v1/example/" + createdId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(createdId))
					.andExpect(jsonPath("$.data.title").value("Integration Test"))
					.andExpect(jsonPath("$.data.active").value(true));
		}
	}

	@Nested
	@DisplayName("수정 흐름은")
	class Describe_update {

		@Test
		@DisplayName("엔티티를 수정하고 변경사항이 반영된다")
		void it_updates_and_persists() throws Exception {
			ExampleEntity entity = exampleRepository.save(ExampleEntity.builder()
					.title("Original")
					.content("Original Content")
					.build());

			ExampleUpdateRequest updateRequest = ExampleUpdateRequest.builder()
					.title("Updated")
					.content("Updated Content")
					.build();

			mockMvc.perform(put("/api/v1/example/" + entity.getId())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(updateRequest)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.title").value("Updated"));

			ExampleEntity updated = exampleRepository.findById(entity.getId()).orElseThrow();
			assertThat(updated.getTitle()).isEqualTo("Updated");
			assertThat(updated.getContent()).isEqualTo("Updated Content");
		}
	}

	@Nested
	@DisplayName("삭제 흐름은")
	class Describe_delete {

		@Test
		@DisplayName("엔티티를 비활성화한다")
		void it_deactivates_entity() throws Exception {
			ExampleEntity entity = exampleRepository.save(ExampleEntity.builder()
					.title("To Delete")
					.content("Content")
					.build());

			mockMvc.perform(delete("/api/v1/example/" + entity.getId()))
					.andExpect(status().isOk());

			ExampleEntity deleted = exampleRepository.findById(entity.getId()).orElseThrow();
			assertThat(deleted.isActive()).isFalse();
		}
	}

	@Nested
	@DisplayName("페이징 조회는")
	class Describe_page {

		@Test
		@DisplayName("활성화된 엔티티만 페이징하여 반환한다")
		void it_returns_paged_active_entities() throws Exception {
			for (int i = 1; i <= 5; i++) {
				exampleRepository.save(ExampleEntity.builder()
						.title("Active " + i)
						.content("Content")
						.build());
			}

			ExampleEntity inactive = ExampleEntity.builder()
					.title("Inactive")
					.content("Content")
					.build();
			inactive.deactivate();
			exampleRepository.save(inactive);

			mockMvc.perform(get("/api/v1/example/page")
							.param("page", "0")
							.param("size", "10"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").isArray())
					.andExpect(jsonPath("$.data.length()").value(5))
					.andExpect(jsonPath("$.page.totalElements").value(5))
					.andExpect(jsonPath("$.meta").exists());
		}
	}

	@Nested
	@DisplayName("응답 메타데이터는")
	class Describe_responseMeta {

		@Test
		@DisplayName("requestId, apiVersion, path, method, timestamp, durationMs를 포함한다")
		void it_includes_all_meta_fields() throws Exception {
			mockMvc.perform(get("/api/v1/example/hello")
							.param("name", "test"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.meta.requestId").exists())
					.andExpect(jsonPath("$.meta.apiVersion").value("v1"))
					.andExpect(jsonPath("$.meta.path").value("/api/v1/example/hello"))
					.andExpect(jsonPath("$.meta.method").value("GET"))
					.andExpect(jsonPath("$.meta.timestamp").exists())
					.andExpect(jsonPath("$.meta.durationMs").isNumber());
		}
	}

	@Nested
	@DisplayName("검증 실패는")
	class Describe_validation {

		@Test
		@DisplayName("400 응답과 필드 오류를 반환한다")
		void it_returns_400_with_field_errors() throws Exception {
			ExampleCreateRequest invalidRequest = ExampleCreateRequest.builder()
					.title("")
					.content("Content")
					.build();

			mockMvc.perform(post("/api/v1/example")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(invalidRequest)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("GLOBAL_002"))
					.andExpect(jsonPath("$.error.fields").isArray())
					.andExpect(jsonPath("$.error.fields[0].field").value("title"));
		}
	}
}
