package com.mjusugangsincheonghelper.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.auth.infrastructure.CustomOidcUserService;
import com.mjusugangsincheonghelper.auth.infrastructure.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.auth.infrastructure.OAuth2LoginSuccessHandler;
import com.mjusugangsincheonghelper.example.dto.ExampleCreateRequest;
import com.mjusugangsincheonghelper.example.dto.ExampleDetailResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleEchoRequest;
import com.mjusugangsincheonghelper.example.dto.ExamplePageItem;
import com.mjusugangsincheonghelper.example.dto.ExampleResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleUpdateRequest;
import com.mjusugangsincheonghelper.example.service.ExampleService;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExampleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@WithMockUser
@DisplayName("ExampleController 슬라이스 테스트")
class ExampleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private ExampleService exampleService;

	@MockitoBean
	private SystemConfigService systemConfigService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@MockitoBean
	private CustomOidcUserService customOidcUserService;

	@MockitoBean
	private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

	@BeforeEach
	void setUp() {
		given(systemConfigService.getBoolean(anyString(), anyBoolean())).willReturn(true);
	}

	@Nested
	@DisplayName("GET /api/v1/example/hello 엔드포인트는")
	class Describe_getHello {

		@Test
		@DisplayName("name 파라미터로 200 응답을 반환한다")
		void it_returns_200_with_name() throws Exception {
			ExampleResponse response = ExampleResponse.of("hello world");
			given(exampleService.hello("world")).willReturn(response);

			mockMvc.perform(get("/api/v1/example/hello")
							.param("name", "world"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.message").value("hello world"))
					.andExpect(jsonPath("$.meta").exists());
		}

		@Test
		@DisplayName("name 파라미터가 없으면 기본값 world를 사용한다")
		void it_uses_default_name_when_not_provided() throws Exception {
			ExampleResponse response = ExampleResponse.of("hello world");
			given(exampleService.hello("world")).willReturn(response);

			mockMvc.perform(get("/api/v1/example/hello"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.message").value("hello world"));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/example/echo 엔드포인트는")
	class Describe_postEcho {

		@Test
		@DisplayName("유효한 요청으로 200 응답을 반환한다")
		void it_returns_200_with_valid_request() throws Exception {
			ExampleEchoRequest request = ExampleEchoRequest.builder()
					.message("test message")
					.build();
			ExampleResponse response = ExampleResponse.of("test message");
			given(exampleService.echo(any(ExampleEchoRequest.class))).willReturn(response);

			mockMvc.perform(post("/api/v1/example/echo")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.message").value("test message"));
		}

		@Test
		@DisplayName("빈 message로 400 응답을 반환한다")
		void it_returns_400_with_empty_message() throws Exception {
			ExampleEchoRequest request = ExampleEchoRequest.builder()
					.message("")
					.build();

			mockMvc.perform(post("/api/v1/example/echo")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("GLOBAL_002"))
					.andExpect(jsonPath("$.error.details").isArray());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/example 엔드포인트는")
	class Describe_postCreate {

		@Test
		@DisplayName("유효한 요청으로 201 응답을 반환한다")
		void it_returns_201_with_valid_request() throws Exception {
			ExampleCreateRequest request = ExampleCreateRequest.builder()
					.title("Test Title")
					.content("Test Content")
					.build();
			ExampleDetailResponse response = ExampleDetailResponse.builder()
					.id(1L)
					.title("Test Title")
					.content("Test Content")
					.active(true)
					.createdAt(Instant.now())
					.updatedAt(Instant.now())
					.build();
			given(exampleService.create(any(ExampleCreateRequest.class))).willReturn(response);

			mockMvc.perform(post("/api/v1/example")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.id").value(1))
					.andExpect(jsonPath("$.data.title").value("Test Title"));
		}

		@Test
		@DisplayName("빈 title로 400 응답을 반환한다")
		void it_returns_400_with_empty_title() throws Exception {
			ExampleCreateRequest request = ExampleCreateRequest.builder()
					.title("")
					.content("Content")
					.build();

			mockMvc.perform(post("/api/v1/example")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("GLOBAL_002"));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/example/{id} 엔드포인트는")
	class Describe_getDetail {

		@Test
		@DisplayName("존재하는 ID로 200 응답을 반환한다")
		void it_returns_200_when_exists() throws Exception {
			ExampleDetailResponse response = ExampleDetailResponse.builder()
					.id(1L)
					.title("Test")
					.content("Content")
					.active(true)
					.createdAt(Instant.now())
					.updatedAt(Instant.now())
					.build();
			given(exampleService.findById(1L)).willReturn(response);

			mockMvc.perform(get("/api/v1/example/1"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(1))
					.andExpect(jsonPath("$.data.title").value("Test"));
		}

		@Test
		@DisplayName("존재하지 않는 ID로 404 응답을 반환한다")
		void it_returns_404_when_not_found() throws Exception {
			given(exampleService.findById(999L))
					.willThrow(new BaseException(ErrorCode.GLOBAL_NOT_FOUND));

			mockMvc.perform(get("/api/v1/example/999"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("GLOBAL_003"));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/example/page 엔드포인트는")
	class Describe_getPage {

		@Test
		@DisplayName("페이징된 결과를 200 응답으로 반환한다")
		void it_returns_200_with_paged_results() throws Exception {
			ExamplePageItem item = ExamplePageItem.builder()
					.id(1L)
					.title("Test")
					.active(true)
					.build();
			Page<ExamplePageItem> page = new PageImpl<>(
					List.of(item),
					PageRequest.of(0, 10),
					1
			);
			given(exampleService.list(0, 10)).willReturn(page);

			mockMvc.perform(get("/api/v1/example/page")
							.param("page", "0")
							.param("size", "10"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").isArray())
					.andExpect(jsonPath("$.page.pageNumber").value(0))
					.andExpect(jsonPath("$.page.pageSize").value(10))
					.andExpect(jsonPath("$.page.totalElements").value(1));
		}
	}

	@Nested
	@DisplayName("PUT /api/v1/example/{id} 엔드포인트는")
	class Describe_putUpdate {

		@Test
		@DisplayName("유효한 요청으로 200 응답을 반환한다")
		void it_returns_200_with_valid_request() throws Exception {
			ExampleUpdateRequest request = ExampleUpdateRequest.builder()
					.title("Updated Title")
					.content("Updated Content")
					.build();
			ExampleDetailResponse response = ExampleDetailResponse.builder()
					.id(1L)
					.title("Updated Title")
					.content("Updated Content")
					.active(true)
					.createdAt(Instant.now())
					.updatedAt(Instant.now())
					.build();
			given(exampleService.update(eq(1L), any(ExampleUpdateRequest.class))).willReturn(response);

			mockMvc.perform(put("/api/v1/example/1")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.title").value("Updated Title"));
		}

		@Test
		@DisplayName("존재하지 않는 ID로 404 응답을 반환한다")
		void it_returns_404_when_not_found() throws Exception {
			ExampleUpdateRequest request = ExampleUpdateRequest.builder()
					.title("Title")
					.content("Content")
					.build();
			given(exampleService.update(eq(999L), any(ExampleUpdateRequest.class)))
					.willThrow(new BaseException(ErrorCode.GLOBAL_NOT_FOUND));

			mockMvc.perform(put("/api/v1/example/999")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/example/{id} 엔드포인트는")
	class Describe_deleteDelete {

		@Test
		@DisplayName("존재하는 ID로 200 응답을 반환한다")
		void it_returns_200_when_exists() throws Exception {
			mockMvc.perform(delete("/api/v1/example/1")
							.with(csrf()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").doesNotExist());

			verify(exampleService).delete(1L);
		}

		@Test
		@DisplayName("존재하지 않는 ID로 404 응답을 반환한다")
		void it_returns_404_when_not_found() throws Exception {
			doThrow(new BaseException(ErrorCode.GLOBAL_NOT_FOUND))
					.when(exampleService).delete(999L);

			mockMvc.perform(delete("/api/v1/example/999")
							.with(csrf()))
					.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/example/error 엔드포인트는")
	class Describe_getError {

		@Test
		@DisplayName("404 응답을 반환한다")
		void it_returns_404() throws Exception {
			doThrow(new BaseException(ErrorCode.GLOBAL_NOT_FOUND))
					.when(exampleService).throwNotFound();

			mockMvc.perform(get("/api/v1/example/error"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("GLOBAL_003"));
		}
	}
}
