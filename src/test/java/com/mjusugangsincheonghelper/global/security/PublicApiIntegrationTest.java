package com.mjusugangsincheonghelper.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증(토큰) 없이 접근 가능해야 하는 공개(Public) API를 검증한다.
 * 공개 체인(publicSecurityFilterChain)과 GET 전용 매칭(PUBLIC_GET_URLS)을 통해
 * 인증 없이도 200을 반환하며, 같은 경로의 비공개 메서드(POST)는 여전히 차단됨을 함께 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("공개 API 통합 테스트")
class PublicApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Nested
	@DisplayName("GET /api/v1/course/sections 엔드포인트는")
	class Describe_courseSections {

		@Test
		@DisplayName("인증 없이 접근해도 200을 반환한다")
		void it_returns_200_without_authentication() throws Exception {
			mockMvc.perform(get("/api/v1/course/sections"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").isArray());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/course/department 엔드포인트는")
	class Describe_courseDepartment {

		@Test
		@DisplayName("인증 없이 접근해도 200을 반환한다")
		void it_returns_200_without_authentication() throws Exception {
			mockMvc.perform(get("/api/v1/course/department"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").isArray());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/exchange/intents/recent 엔드포인트는")
	class Describe_exchangeRecentIntents {

		@Test
		@DisplayName("인증 없이 접근해도 200을 반환한다")
		void it_returns_200_without_authentication() throws Exception {
			mockMvc.perform(get("/api/v1/exchange/intents/recent"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.recentIntents").isArray());
		}
	}

	@Nested
	@DisplayName("같은 경로의 비공개 메서드는")
	class Describe_nonPublicMethods {

		@Test
		@DisplayName("POST /api/v1/course/sections는 인증 없이 차단된다")
		void it_blocks_post_without_authentication() throws Exception {
			SecurityContextHolder.clearContext();

			mockMvc.perform(post("/api/v1/course/sections")
							.contentType(MediaType.APPLICATION_JSON)
							.content("[]"))
					.andExpect(status().is4xxClientError());
		}
	}
}
