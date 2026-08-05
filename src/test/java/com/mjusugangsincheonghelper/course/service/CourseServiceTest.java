package com.mjusugangsincheonghelper.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.course.dto.CourseDepartmentResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionResponse;
import com.mjusugangsincheonghelper.database.entity.CourseEntity;
import com.mjusugangsincheonghelper.database.repository.CourseRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseService 단위 테스트")
class CourseServiceTest {

	@Mock
	private CourseRepository courseRepository;

	@InjectMocks
	private CourseService courseService;

	@Nested
	@DisplayName("findDepartments 메서드는")
	class Describe_findDepartments {

		@Test
		@DisplayName("요청 학기에 데이터가 있으면 해당 학기 학과 목록을 반환한다")
		void it_returns_departments_when_term_has_data() {
			given(courseRepository.existsByTerm("202525")).willReturn(true);
			given(courseRepository.findDistinctDepartmentsByTerm("202525"))
					.willReturn(List.<Object[]>of(new Object[]{"15610", "컴퓨터공학부", "10"}));

			List<CourseDepartmentResponse> result = courseService.findDepartments("202525");

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getDeptcd()).isEqualTo("15610");
			assertThat(result.get(0).getDeptnm()).isEqualTo("컴퓨터공학부");
			assertThat(result.get(0).getCampusdiv()).isEqualTo("10");
			verify(courseRepository, times(1)).existsByTerm("202525");
			verify(courseRepository, times(1)).findDistinctDepartmentsByTerm("202525");
		}

		@Test
		@DisplayName("겨울학기에 데이터가 없으면 같은 해 여름학기로 폴백한다 (202525 → 202515)")
		void it_falls_back_from_semester_2_to_semester_1() {
			given(courseRepository.existsByTerm("202525")).willReturn(false);
			given(courseRepository.existsByTerm("202515")).willReturn(true);
			given(courseRepository.findDistinctDepartmentsByTerm("202515"))
					.willReturn(List.<Object[]>of(new Object[]{"15809", "수학과", "20"}));

			List<CourseDepartmentResponse> result = courseService.findDepartments("202525");

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getDeptnm()).isEqualTo("수학과");
			assertThat(result.get(0).getCampusdiv()).isEqualTo("20");
			verify(courseRepository, times(1)).existsByTerm("202525");
			verify(courseRepository, times(1)).existsByTerm("202515");
			verify(courseRepository, times(1)).findDistinctDepartmentsByTerm("202515");
		}

		@Test
		@DisplayName("여름학기에 데이터가 없으면 작년 겨울학기로 폴백한다 (202515 → 202425)")
		void it_falls_back_from_semester_1_to_previous_year_semester_2() {
			given(courseRepository.existsByTerm("202515")).willReturn(false);
			given(courseRepository.existsByTerm("202425")).willReturn(true);
			given(courseRepository.findDistinctDepartmentsByTerm("202425"))
					.willReturn(List.<Object[]>of(new Object[]{"15430", "기계시스템공학부", "10"}));

			List<CourseDepartmentResponse> result = courseService.findDepartments("202515");

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getDeptnm()).isEqualTo("기계시스템공학부");
			verify(courseRepository, times(1)).existsByTerm("202515");
			verify(courseRepository, times(1)).existsByTerm("202425");
			verify(courseRepository, times(1)).findDistinctDepartmentsByTerm("202425");
		}

		@Test
		@DisplayName("2학기에 데이터가 없으면 같은 해 1학기로 폴백한다 (202620 → 202610)")
		void it_falls_back_from_2_semester_to_1_semester_same_year() {
			given(courseRepository.existsByTerm("202620")).willReturn(false);
			given(courseRepository.existsByTerm("202610")).willReturn(true);
			given(courseRepository.findDistinctDepartmentsByTerm("202610"))
					.willReturn(List.<Object[]>of(new Object[]{"15610", "컴퓨터공학부", "20"}));

			List<CourseDepartmentResponse> result = courseService.findDepartments("202620");

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getDeptnm()).isEqualTo("컴퓨터공학부");
			assertThat(result.get(0).getCampusdiv()).isEqualTo("20");
			verify(courseRepository, times(1)).existsByTerm("202620");
			verify(courseRepository, times(1)).existsByTerm("202610");
			verify(courseRepository, times(1)).findDistinctDepartmentsByTerm("202610");
		}

		@Test
		@DisplayName("1학기에 데이터가 없으면 작년 2학기로 폴백한다 (202610 → 202520)")
		void it_falls_back_from_1_semester_to_previous_year_2_semester() {
			given(courseRepository.existsByTerm("202610")).willReturn(false);
			given(courseRepository.existsByTerm("202520")).willReturn(true);
			given(courseRepository.findDistinctDepartmentsByTerm("202520"))
					.willReturn(List.<Object[]>of(new Object[]{"15430", "기계시스템공학부", "10"}));

			List<CourseDepartmentResponse> result = courseService.findDepartments("202610");

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getDeptnm()).isEqualTo("기계시스템공학부");
			verify(courseRepository, times(1)).existsByTerm("202610");
			verify(courseRepository, times(1)).existsByTerm("202520");
			verify(courseRepository, times(1)).findDistinctDepartmentsByTerm("202520");
		}

		@Test
		@DisplayName("20회 폴백 이후에도 데이터가 없으면 빈 목록을 반환한다")
		void it_returns_empty_after_exhausting_fallbacks() {
			given(courseRepository.existsByTerm(org.mockito.ArgumentMatchers.anyString()))
					.willReturn(false);

			List<CourseDepartmentResponse> result = courseService.findDepartments("202525");

			assertThat(result).isEmpty();
			verify(courseRepository, times(20)).existsByTerm(org.mockito.ArgumentMatchers.anyString());
		}
	}

	@Nested
	@DisplayName("findSections 메서드는")
	class Describe_findSections {

		private CourseEntity course(String coursecls, String lecttime) {
			return CourseEntity.builder()
					.coursecls(coursecls)
					.term("202525")
					.lecttime(lecttime)
					.build();
		}

		@Test
		@DisplayName("excludeDays에 해당하는 요일에 수업이 있으면 제외한다")
		void it_excludes_courses_meeting_on_excluded_days() {
			given(courseRepository.existsByTerm("202525")).willReturn(true);
			given(courseRepository.searchSections("202525", null, null, null))
					.willReturn(List.of(
							course("0001", "월10:00~10:50"),
							course("0002", "수10:00~10:50"),
							course("0003", "화11:00~11:50, 목11:00~11:50")
					));

			List<CourseSectionResponse> result = courseService.findSections("202525", null, null, null, List.of("1", "4"));

			assertThat(result).extracting(CourseSectionResponse::getCoursecls)
					.containsExactly("0002");
		}

		@Test
		@DisplayName("시간 미지정(lecttime 없음) 강좌는 excludeDays와 무관하게 포함한다")
		void it_keeps_courses_without_schedule() {
			given(courseRepository.existsByTerm("202525")).willReturn(true);
			given(courseRepository.searchSections("202525", null, null, null))
					.willReturn(List.of(
							course("0001", null),
							course("0002", "")
					));

			List<CourseSectionResponse> result = courseService.findSections("202525", null, null, null, List.of("1"));

			assertThat(result).extracting(CourseSectionResponse::getCoursecls)
					.containsExactly("0001", "0002");
		}

		@Test
		@DisplayName("excludeDays가 없으면 모든 강좌를 반환한다")
		void it_returns_all_when_no_exclude_days() {
			given(courseRepository.existsByTerm("202525")).willReturn(true);
			given(courseRepository.searchSections("202525", null, null, null))
					.willReturn(List.of(
							course("0001", "월10:00~10:50"),
							course("0002", "토09:00~12:00")
					));

			List<CourseSectionResponse> result = courseService.findSections("202525", null, null, null, null);

			assertThat(result).extracting(CourseSectionResponse::getCoursecls)
					.containsExactly("0001", "0002");
		}

		@Test
		@DisplayName("요청 학기에 강좌 데이터가 없으면 직전 학기로 폴백하여 조회한다 (202620 → 202610)")
		void it_falls_back_to_previous_term_when_no_data() {
			given(courseRepository.existsByTerm("202620")).willReturn(false);
			given(courseRepository.existsByTerm("202610")).willReturn(true);
			given(courseRepository.searchSections("202610", null, null, null))
					.willReturn(List.of(course("0001", "월10:00~10:50")));

			List<CourseSectionResponse> result = courseService.findSections("202620", null, null, null, null);

			assertThat(result).extracting(CourseSectionResponse::getCoursecls)
					.containsExactly("0001");
			verify(courseRepository, times(1)).existsByTerm("202620");
			verify(courseRepository, times(1)).existsByTerm("202610");
			verify(courseRepository, times(1)).searchSections("202610", null, null, null);
		}

		@Test
		@DisplayName("폴백한 학기에서도 검색 필터(deptcd/keyword/campus/excludeDays)가 그대로 적용된다")
		void it_applies_filters_after_fallback() {
			given(courseRepository.existsByTerm("202620")).willReturn(false);
			given(courseRepository.existsByTerm("202610")).willReturn(true);
			given(courseRepository.searchSections("202610", "15611", "10", "%알고%"))
					.willReturn(List.of(
							course("0001", "월10:00~10:50"),
							course("0002", "수10:00~10:50")
					));

			List<CourseSectionResponse> result = courseService.findSections(
					"202620", "15611", "10", "알고", List.of("1"));

			assertThat(result).extracting(CourseSectionResponse::getCoursecls)
					.containsExactly("0002");
			verify(courseRepository, times(1)).searchSections("202610", "15611", "10", "%알고%");
		}

		@Test
		@DisplayName("학기에 데이터가 있는데 검색 결과만 비어 있으면 폴백하지 않고 빈 목록을 반환한다")
		void it_does_not_fall_back_when_term_has_data_but_no_match() {
			given(courseRepository.existsByTerm("202525")).willReturn(true);
			given(courseRepository.searchSections("202525", null, null, "%없는과목%"))
					.willReturn(List.of());

			List<CourseSectionResponse> result = courseService.findSections("202525", null, null, "없는과목", null);

			assertThat(result).isEmpty();
			verify(courseRepository, times(1)).existsByTerm("202525");
			verify(courseRepository, times(1)).searchSections("202525", null, null, "%없는과목%");
			verify(courseRepository, never()).existsByTerm("202515");
		}

		@Test
		@DisplayName("20회 폴백 이후에도 데이터가 없으면 빈 목록을 반환한다")
		void it_returns_empty_after_exhausting_fallbacks() {
			given(courseRepository.existsByTerm(org.mockito.ArgumentMatchers.anyString()))
					.willReturn(false);

			List<CourseSectionResponse> result = courseService.findSections("202525", null, null, null, null);

			assertThat(result).isEmpty();
			verify(courseRepository, times(20)).existsByTerm(org.mockito.ArgumentMatchers.anyString());
		}
	}
}
