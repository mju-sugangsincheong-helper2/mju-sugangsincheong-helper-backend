package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.singlegame.config.SingleGameProperties;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse;
import com.mjusugangsincheonghelper.singlegame.dto.DepartmentsResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import com.mjusugangsincheonghelper.singlegame.dto.cache.FeedbackQuantileTable;
import com.mjusugangsincheonghelper.singlegame.dto.cache.QuantileBand;
import com.mjusugangsincheonghelper.singlegame.dto.cache.SequenceQuantileTable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SingleGameSaveService 단위 테스트")
class SingleGameSaveServiceTest {

	@Mock
	private SingleGameRepository singleGameRepository;

	@Mock
	private SingleGameDetailRepository singleGameDetailRepository;

	@Mock
	private MemberRepository memberRepository;

	private SingleGameSaveService saveService;

	@Captor
	private ArgumentCaptor<SingleGameEntity> gameCaptor;

	@Captor
	private ArgumentCaptor<List<SingleGameDetailEntity>> detailsCaptor;

	@BeforeEach
	void setUp() {
		saveService = new SingleGameSaveService(
				singleGameRepository, singleGameDetailRepository, memberRepository, new SingleGameProperties());
	}

	@Nested
	@DisplayName("saveGame 메서드는")
	class Describe_saveGame {

		@Test
		@DisplayName("유효한 요청을 받으면 게임을 저장하고 응답을 반환한다")
		void it_saves_game_and_returns_response() throws Exception {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(1000).tClickYes(500).tClickOk(300).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);
			given(singleGameRepository.save(any())).willAnswer(invocation -> {
				SingleGameEntity entity = invocation.getArgument(0);
				Field idField = SingleGameEntity.class.getDeclaredField("id");
				idField.setAccessible(true);
				idField.set(entity, 100L);
				return entity;
			});

			SingleGameSaveResponse response = saveService.saveGame(1L, request);

			assertThat(response.getGameId()).isEqualTo(100L);
			assertThat(response.getMessage()).isNotNull();

			verify(singleGameRepository).save(gameCaptor.capture());
			SingleGameEntity saved = gameCaptor.getValue();
			assertThat(saved.getTTotal()).isEqualTo(2000 + 6 * 1800);
			assertThat(saved.getTotalCourses()).isEqualTo(6);
			assertThat(saved.isCompleted()).isTrue();

			verify(singleGameDetailRepository).saveAll(detailsCaptor.capture());
			assertThat(detailsCaptor.getValue()).hasSize(6);
			assertThat(detailsCaptor.getValue().get(0).getGameId()).isEqualTo(100L);
		}

		@Test
		@DisplayName("존재하지 않는 회원이면 예외를 던진다")
		void it_throws_when_member_not_found() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(List.of())
					.build();

			given(memberRepository.existsById(999L)).willReturn(false);

			assertThatThrownBy(() -> saveService.saveGame(999L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("허용되지 않은 totalCourses이면 예외를 던진다")
		void it_throws_when_total_courses_invalid() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(4).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 0이면 예외를 던진다")
		void it_throws_when_total_courses_is_zero() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(0).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 2이면 예외를 던진다")
		void it_throws_when_total_courses_is_two() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(2).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 5이면 예외를 던진다")
		void it_throws_when_total_courses_is_five() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(5).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 9이면 예외를 던진다")
		void it_throws_when_total_courses_is_nine() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(9).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 음수이면 예외를 던진다")
		void it_throws_when_total_courses_is_negative() {
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(-5).completed(true).tEnterMain(2000)
					.details(List.of(SingleGameDetailRequest.builder()
							.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build()))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("totalCourses가 1이면 정상 저장된다")
		void it_saves_when_total_courses_is_one() throws Exception {
			SingleGameDetailRequest detail = SingleGameDetailRequest.builder()
					.sequence(1).tClickCourse(1000).tClickYes(500).tClickOk(300)
					.build();
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(1).completed(true).tEnterMain(2000)
					.details(List.of(detail))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);
			given(singleGameRepository.save(any())).willAnswer(invocation -> {
				SingleGameEntity entity = invocation.getArgument(0);
				Field idField = SingleGameEntity.class.getDeclaredField("id");
				idField.setAccessible(true);
				idField.set(entity, 100L);
				return entity;
			});

			SingleGameSaveResponse response = saveService.saveGame(1L, request);

			assertThat(response.getGameId()).isEqualTo(100L);
		}

		@Test
		@DisplayName("totalCourses가 7이면 정상 저장된다")
		void it_saves_when_total_courses_is_seven() throws Exception {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 7; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(1000).tClickYes(500).tClickOk(300).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(7).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);
			given(singleGameRepository.save(any())).willAnswer(invocation -> {
				SingleGameEntity entity = invocation.getArgument(0);
				Field idField = SingleGameEntity.class.getDeclaredField("id");
				idField.setAccessible(true);
				idField.set(entity, 100L);
				return entity;
			});

			SingleGameSaveResponse response = saveService.saveGame(1L, request);

			assertThat(response.getGameId()).isEqualTo(100L);
		}

		@Test
		@DisplayName("totalCourses가 8이면 정상 저장된다")
		void it_saves_when_total_courses_is_eight() throws Exception {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 8; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(1000).tClickYes(500).tClickOk(300).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(8).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);
			given(singleGameRepository.save(any())).willAnswer(invocation -> {
				SingleGameEntity entity = invocation.getArgument(0);
				Field idField = SingleGameEntity.class.getDeclaredField("id");
				idField.setAccessible(true);
				idField.set(entity, 100L);
				return entity;
			});

			SingleGameSaveResponse response = saveService.saveGame(1L, request);

			assertThat(response.getGameId()).isEqualTo(100L);
		}

		@Test
		@DisplayName("completed=true인데 details 개수가 totalCourses와 다르면 예외를 던진다")
		void it_throws_when_completed_but_details_count_mismatch() {
			SingleGameDetailRequest detail1 = SingleGameDetailRequest.builder()
					.sequence(1).tClickCourse(100).tClickYes(100).tClickOk(100).build();
			SingleGameDetailRequest detail2 = SingleGameDetailRequest.builder()
					.sequence(2).tClickCourse(100).tClickYes(100).tClickOk(100).build();
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(List.of(detail1, detail2))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("completed=false인데 details 개수가 totalCourses와 같으면 예외를 던진다")
		void it_throws_when_not_completed_but_details_count_equals_total() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(false).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("completed=false이고 details 개수가 totalCourses보다 적으면 정상 저장된다")
		void it_saves_when_not_completed_and_details_less_than_total() throws Exception {
			SingleGameDetailRequest detail = SingleGameDetailRequest.builder()
					.sequence(1).tClickCourse(1000).tClickYes(500).tClickOk(300)
					.build();
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(false).tEnterMain(2000)
					.details(List.of(detail))
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);
			given(singleGameRepository.save(any())).willAnswer(invocation -> {
				SingleGameEntity entity = invocation.getArgument(0);
				Field idField = SingleGameEntity.class.getDeclaredField("id");
				idField.setAccessible(true);
				idField.set(entity, 100L);
				return entity;
			});

			SingleGameSaveResponse response = saveService.saveGame(1L, request);

			assertThat(response.getGameId()).isEqualTo(100L);
		}

		@Test
		@DisplayName("completed=true이고 details 개수가 totalCourses와 같으면 정상 저장된다")
		void it_saves_when_completed_and_details_count_matches() throws Exception {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);
			given(singleGameRepository.save(any())).willAnswer(invocation -> {
				SingleGameEntity entity = invocation.getArgument(0);
				Field idField = SingleGameEntity.class.getDeclaredField("id");
				idField.setAccessible(true);
				idField.set(entity, 100L);
				return entity;
			});

			SingleGameSaveResponse response = saveService.saveGame(1L, request);

			assertThat(response.getGameId()).isEqualTo(100L);
		}

		@Test
		@DisplayName("tEnterMain이 최소값 미만이면 예외를 던진다")
		void it_throws_when_t_enter_main_below_min() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(0)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tEnterMain이 최대값 초과이면 예외를 던진다")
		void it_throws_when_t_enter_main_above_max() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(65000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickCourse가 최소값 미만이면 예외를 던진다")
		void it_throws_when_t_click_course_below_min() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(0).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickCourse가 최대값 초과이면 예외를 던진다")
		void it_throws_when_t_click_course_above_max() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(65000).tClickYes(100).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickYes가 최소값 미만이면 예외를 던진다")
		void it_throws_when_t_click_yes_below_min() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(0).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickYes가 최대값 초과이면 예외를 던진다")
		void it_throws_when_t_click_yes_above_max() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(65000).tClickOk(100).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickOk가 최소값 미만이면 예외를 던진다")
		void it_throws_when_t_click_ok_below_min() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(0).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("tClickOk가 최대값 초과이면 예외를 던진다")
		void it_throws_when_t_click_ok_above_max() {
			List<SingleGameDetailRequest> details = new java.util.ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				details.add(SingleGameDetailRequest.builder()
						.sequence(i).tClickCourse(100).tClickYes(100).tClickOk(65000).build());
			}
			SingleGameSaveRequest request = SingleGameSaveRequest.builder()
					.totalCourses(6).completed(true).tEnterMain(2000)
					.details(details)
					.build();

			given(memberRepository.existsById(1L)).willReturn(true);

			assertThatThrownBy(() -> saveService.saveGame(1L, request))
					.isInstanceOf(BaseException.class);
		}
	}
}
