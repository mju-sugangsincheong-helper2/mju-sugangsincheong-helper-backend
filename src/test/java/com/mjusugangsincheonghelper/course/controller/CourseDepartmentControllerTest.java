package com.mjusugangsincheonghelper.course.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mjusugangsincheonghelper.course.dto.CourseDepartmentResponse;
import com.mjusugangsincheonghelper.course.service.CourseService;
import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseDepartmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("CourseDepartmentController 슬라이스 테스트")
class CourseDepartmentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CourseService courseService;

	@MockitoBean
	private SystemConfigService systemConfigService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@Nested
	@DisplayName("GET /api/v1/course/department 엔드포인트는")
	class Describe_findDepartments {

		@Test
		@DisplayName("current_term 기준 학과 목록을 반환한다")
		void it_returns_departments_for_current_term() throws Exception {
			given(systemConfigService.getCurrentTerm()).willReturn("202515");
			given(courseService.findDepartments("202515"))
					.willReturn(List.of(
							CourseDepartmentResponse.builder().deptcd("15610").deptnm("컴퓨터공학부").build(),
							CourseDepartmentResponse.builder().deptcd("15808").deptnm("물리학과").build()
					));

			mockMvc.perform(get("/api/v1/course/department"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].deptcd").value("15610"))
					.andExpect(jsonPath("$.data[0].deptnm").value("컴퓨터공학부"))
					.andExpect(jsonPath("$.data[1].deptnm").value("물리학과"));
		}

		@Test
		@DisplayName("강좌 데이터가 없으면 빈 목록을 반환한다")
		void it_returns_empty_list_when_no_departments() throws Exception {
			given(systemConfigService.getCurrentTerm()).willReturn("202515");
			given(courseService.findDepartments("202515")).willReturn(List.of());

			mockMvc.perform(get("/api/v1/course/department"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").isEmpty());
		}

		@Test
		@DisplayName("term 파라미터를 지정하면 해당 학기의 학과 목록을 반환한다")
		void it_returns_departments_for_given_term() throws Exception {
			given(courseService.findDepartments("202525"))
					.willReturn(List.of(
							CourseDepartmentResponse.builder().deptcd("15809").deptnm("수학과").build()
					));

			mockMvc.perform(get("/api/v1/course/department").param("term", "202525"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].deptnm").value("수학과"));
		}
	}
}
