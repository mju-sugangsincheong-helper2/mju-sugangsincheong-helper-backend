package com.mjusugangsincheonghelper.notice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.notice.dto.NoticeRequest;
import com.mjusugangsincheonghelper.notice.dto.NoticeResponse;
import com.mjusugangsincheonghelper.notice.service.NoticeService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoticeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("NoticeController 테스트")
class NoticeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private NoticeService noticeService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void setUp() {
		Authentication authentication = mock(Authentication.class);
		given(authentication.getPrincipal()).willReturn(1L);

		SecurityContext securityContext = mock(SecurityContext.class);
		given(securityContext.getAuthentication()).willReturn(authentication);

		SecurityContextHolder.setContext(securityContext);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("공지 목록 조회 API가 200 OK를 반환한다")
	void shouldFindAllNotices() throws Exception {
		NoticeResponse notice = NoticeResponse.builder()
				.id(1L)
				.type("general")
				.title("공지 제목")
				.content("공지 내용")
				.createdAt(Instant.parse("2026-01-01T00:00:00Z"))
				.build();

		given(noticeService.findAll()).willReturn(List.of(notice));

		mockMvc.perform(get("/api/v1/notices"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(1))
				.andExpect(jsonPath("$.data[0].title").value("공지 제목"))
				.andExpect(jsonPath("$.data[0].type").value("general"));
	}

	@Test
	@DisplayName("공지 등록 API가 200 OK를 반환한다")
	void shouldCreateNotice() throws Exception {
		String body = "{\"type\":\"update\",\"title\":\"신규 공지\",\"content\":\"내용입니다.\"}";

		given(noticeService.create(any(NoticeRequest.class)))
				.willReturn(NoticeResponse.builder()
						.id(1L)
						.type("update")
						.title("신규 공지")
						.content("내용입니다.")
						.build());

		mockMvc.perform(post("/api/v1/notices")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.data.title").value("신규 공지"));
	}

	@Test
	@DisplayName("공지 수정 API가 200 OK를 반환한다")
	void shouldUpdateNotice() throws Exception {
		given(noticeService.update(eq(1L), any(NoticeRequest.class)))
				.willReturn(NoticeResponse.builder()
						.id(1L)
						.type("general")
						.title("수정된 제목")
						.content("수정된 내용")
						.build());

		mockMvc.perform(put("/api/v1/notices/1")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"general\",\"title\":\"수정된 제목\",\"content\":\"수정된 내용\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("수정된 제목"));
	}

	@Test
	@DisplayName("공지 삭제 API가 200 OK를 반환한다")
	void shouldDeleteNotice() throws Exception {
		doNothing().when(noticeService).delete(1L);

		mockMvc.perform(delete("/api/v1/notices/1")
						.with(csrf()))
				.andExpect(status().isOk());
	}
}
