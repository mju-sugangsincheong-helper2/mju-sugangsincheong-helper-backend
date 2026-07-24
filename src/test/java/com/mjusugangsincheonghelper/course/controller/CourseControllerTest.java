package com.mjusugangsincheonghelper.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.course.dto.CourseSectionDeleteResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportRequest;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionResponse;
import com.mjusugangsincheonghelper.course.service.CourseService;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@WithMockUser
@DisplayName("CourseController 슬라이스 테스트")
class CourseControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private CourseService courseService;

	@MockitoBean
	private SystemConfigService systemConfigService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void setUp() {
	}

	@Nested
	@DisplayName("POST /api/v1/course/sections 엔드포인트는")
	class Describe_importSections {

		@Test
		@DisplayName("강좌 목록을 받아 등록하고 200 응답을 반환한다")
		void it_returns_200_with_imported_count() throws Exception {
			CourseSectionImportRequest item = CourseSectionImportRequest.builder()
					.curiyear("2026").curismt("15")
					.coursecls("0001").curinum("KMA00101").curinm("성서와인간이해")
					.profnm("김진옥").lecttime("월10:00~11:50").lecperiod("2026-06-22 ~ 2026-07-10")
					.cdtnum("2").cdttime("2").takelim("40").listennow("40")
					.build();

			CourseSectionImportResponse serviceResponse = CourseSectionImportResponse.builder()
					.importedCount(1)
					.terms(List.of("202615"))
					.build();
			given(courseService.importSections(any())).willReturn(serviceResponse);

			mockMvc.perform(post("/api/v1/course/sections")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(List.of(item))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.importedCount").value(1))
					.andExpect(jsonPath("$.data.terms[0]").value("202615"))
					.andExpect(jsonPath("$.meta").exists());
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/course/sections 엔드포인트는")
	class Describe_deleteSections {

		@Test
		@DisplayName("term 파라미터로 강좌를 삭제하고 200 응답을 반환한다")
		void it_deletes_by_term_and_returns_200() throws Exception {
			CourseSectionDeleteResponse serviceResponse = CourseSectionDeleteResponse.builder()
					.deletedCount(5)
					.build();
			given(courseService.deleteSectionsByTerm("202615")).willReturn(serviceResponse);

			mockMvc.perform(delete("/api/v1/course/sections")
							.param("term", "202615"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.deletedCount").value(5))
					.andExpect(jsonPath("$.meta").exists());
		}

		@Test
		@DisplayName("term 파라미터가 없으면 400 응답을 반환한다")
		void it_returns_400_without_term() throws Exception {
			mockMvc.perform(delete("/api/v1/course/sections"))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/course/sections 엔드포인트는")
	class Describe_findSections {

		@Test
		@DisplayName("term 없이 호출하면 current_term을 조회하여 강좌 목록을 반환한다")
		void it_returns_sections_with_current_term() throws Exception {
			CourseSectionResponse item = CourseSectionResponse.builder()
					.coursecls("0001").term("202615")
					.curinum("KMA00101").curinm("성서와인간이해")
					.profnm("김진옥").cdtnum("2").cdttime("2")
					.takelim("40").listennow("40")
					.build();
			given(systemConfigService.getCurrentTerm()).willReturn("202615");
			given(courseService.findSections("202615")).willReturn(List.of(item));

			mockMvc.perform(get("/api/v1/course/sections"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].coursecls").value("0001"))
					.andExpect(jsonPath("$.data[0].term").value("202615"))
					.andExpect(jsonPath("$.data[0].curinm").value("성서와인간이해"))
					.andExpect(jsonPath("$.meta").exists());
		}

		@Test
		@DisplayName("term 파라미터로 특정 학기 강좌를 조회한다")
		void it_returns_sections_filtered_by_term() throws Exception {
			CourseSectionResponse item = CourseSectionResponse.builder()
					.coursecls("0001").term("202615")
					.curinum("KMA00101").curinm("성서와인간이해")
					.profnm("김진옥").cdtnum("2").cdttime("2")
					.takelim("40").listennow("40")
					.build();
			given(courseService.findSections("202615")).willReturn(List.of(item));

			mockMvc.perform(get("/api/v1/course/sections")
							.param("term", "202615"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].term").value("202615"));
		}
	}
}
