package com.mjusugangsincheonghelper.course.service;

import com.mjusugangsincheonghelper.course.dto.CourseSectionDeleteResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportRequest;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionResponse;
import com.mjusugangsincheonghelper.database.entity.CourseEntity;
import com.mjusugangsincheonghelper.database.repository.CourseRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseService 단위 테스트")
class CourseServiceTest {

	@Mock
	private CourseRepository courseRepository;

	@InjectMocks
	private CourseService courseService;

	@Captor
	private ArgumentCaptor<List<CourseEntity>> entityCaptor;

	@Nested
	@DisplayName("importSections 메서드는")
	class Describe_importSections {

		@Test
		@DisplayName("요청을 엔티티로 변환하여 저장하고 가공된 응답을 반환한다")
		void it_saves_entities_and_returns_response() {
			CourseSectionImportRequest item1 = CourseSectionImportRequest.builder()
					.curiyear("2026").curismt("15")
					.coursecls("0001").curinum("KMA00101").curinm("성서와인간이해")
					.profnm("김진옥").lecttime("월10:00~11:50").lecperiod("2026-06-22 ~ 2026-07-10")
					.cdtnum("2").cdttime("2").takelim("40").listennow("40")
					.build();
			CourseSectionImportRequest item2 = CourseSectionImportRequest.builder()
					.curiyear("2026").curismt("15")
					.coursecls("0002").curinum("KMA00102").curinm("철학의이해")
					.profnm("홍길동").lecttime("화10:00~11:50").lecperiod("2026-06-22 ~ 2026-07-10")
					.cdtnum("3").cdttime("3").takelim("30").listennow("25")
					.build();

			List<CourseSectionImportRequest> items = List.of(item1, item2);
			given(courseRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

			CourseSectionImportResponse response = courseService.importSections(items);

			assertThat(response.getImportedCount()).isEqualTo(2);
			assertThat(response.getTerms()).containsExactly("202615");
			verify(courseRepository).saveAll(entityCaptor.capture());

			List<CourseEntity> saved = entityCaptor.getValue();
			assertThat(saved).hasSize(2);
			assertThat(saved.get(0).getTerm()).isEqualTo("202615");
			assertThat(saved.get(0).getCurinm()).isEqualTo("성서와인간이해");
			assertThat(saved.get(1).getTerm()).isEqualTo("202615");
			assertThat(saved.get(1).getCurinm()).isEqualTo("철학의이해");
		}

		@Test
		@DisplayName("여러 term의 데이터를 처리하면 중복 제거된 term 목록을 반환한다")
		void it_returns_distinct_terms_when_multiple_terms() {
			CourseSectionImportRequest spring = CourseSectionImportRequest.builder()
					.curiyear("2026").curismt("10").coursecls("0001").build();
			CourseSectionImportRequest summer = CourseSectionImportRequest.builder()
					.curiyear("2026").curismt("15").coursecls("0002").build();

			List<CourseSectionImportRequest> items = List.of(spring, summer);
			given(courseRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

			CourseSectionImportResponse response = courseService.importSections(items);

			assertThat(response.getImportedCount()).isEqualTo(2);
			assertThat(response.getTerms()).containsExactly("202610", "202615");
		}
	}

	@Nested
	@DisplayName("deleteSectionsByTerm 메서드는")
	class Describe_deleteSectionsByTerm {

		@Test
		@DisplayName("term에 해당하는 강좌를 삭제하고 삭제된 개수를 반환한다")
		void it_deletes_by_term_and_returns_count() {
			given(courseRepository.deleteByTerm("202615")).willReturn(3L);

			CourseSectionDeleteResponse response = courseService.deleteSectionsByTerm("202615");

			assertThat(response.getDeletedCount()).isEqualTo(3);
			verify(courseRepository).deleteByTerm("202615");
		}

		@Test
		@DisplayName("해당 term의 데이터가 없으면 0을 반환한다")
		void it_returns_0_when_no_data() {
			given(courseRepository.deleteByTerm("202615")).willReturn(0L);

			CourseSectionDeleteResponse response = courseService.deleteSectionsByTerm("202615");

			assertThat(response.getDeletedCount()).isEqualTo(0);
			verify(courseRepository).deleteByTerm("202615");
		}
	}

	@Nested
	@DisplayName("findSections 메서드는")
	class Describe_findSections {

		@Test
		@DisplayName("term을 지정하면 해당 학기 강좌만 반환한다")
		void it_returns_sections_filtered_by_term() {
			CourseEntity entity = CourseEntity.builder()
					.coursecls("0001").term("202615")
					.curinum("KMA00101").curinm("성서와인간이해")
					.profnm("김진옥").lecttime("월10:00~11:50")
					.lecperiod("2026-06-22 ~ 2026-07-10")
					.cdtnum("2").cdttime("2").takelim("40").listennow("40")
					.build();
			given(courseRepository.findByTerm("202615")).willReturn(List.of(entity));

			List<CourseSectionResponse> result = courseService.findSections("202615");

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getTerm()).isEqualTo("202615");
			assertThat(result.get(0).getCurinm()).isEqualTo("성서와인간이해");
			verify(courseRepository).findByTerm("202615");
		}

		@Test
		@DisplayName("term이 null이면 전체 강좌를 반환한다")
		void it_returns_all_sections_when_term_is_null() {
			given(courseRepository.findAll()).willReturn(List.of());

			List<CourseSectionResponse> result = courseService.findSections(null);

			assertThat(result).isEmpty();
			verify(courseRepository).findAll();
		}

		@Test
		@DisplayName("term이 빈 문자열이면 전체 강좌를 반환한다")
		void it_returns_all_sections_when_term_is_blank() {
			given(courseRepository.findAll()).willReturn(List.of());

			List<CourseSectionResponse> result = courseService.findSections("  ");

			assertThat(result).isEmpty();
			verify(courseRepository).findAll();
		}
	}
}
