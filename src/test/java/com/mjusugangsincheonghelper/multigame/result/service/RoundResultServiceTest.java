package com.mjusugangsincheonghelper.multigame.result.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundLogEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundMemberEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundLogRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundMemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundRecordResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundResult;
import com.mjusugangsincheonghelper.multigame.result.dto.RoundDetailResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.RoundSummaryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoundResultService 단위 테스트")
class RoundResultServiceTest {

	private static final String START_TIME = "20260801120000";

	@Mock
	private MultigameRoundRepository roundRepository;

	@Mock
	private MultigameRoundMemberRepository memberRepository;

	@Mock
	private MultigameRoundLogRepository logRepository;

	private RoundResultService service;

	@BeforeEach
	void setUp() {
		service = new RoundResultService(roundRepository, memberRepository, logRepository);
	}

	private MultigameRoundEntity round() {
		return MultigameRoundEntity.builder()
				.startTime(START_TIME)
				.participantCount(50)
				.capacity(5)
				.build();
	}

	private MultigameRoundMemberEntity member() {
		return MultigameRoundMemberEntity.builder()
				.startTime(START_TIME)
				.memberId(1L)
				.subjectId(2)
				.status("SUCCESS")
				.build();
	}

	private MultigameRoundLogEntity log() {
		return MultigameRoundLogEntity.builder()
				.startTime(START_TIME)
				.memberId(1L)
				.subjectId(2)
				.attemptStatus("ENQUEUED")
				.attemptSeq(3L)
				.currentLimit(1)
				.attemptedAt(Instant.parse("2026-08-01T12:00:01Z"))
				.build();
	}

	// ---------------------------------------------------------------------
	// rounds
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("rounds 메서드는")
	class Describe_rounds {

		@Test
		@DisplayName("라운드 목록을 요약 응답으로 매핑하여 페이지로 반환한다")
		void it_returns_round_summaries() {
			given(roundRepository.findAllByOrderByStartTimeDesc(any(Pageable.class)))
					.willReturn(new PageImpl<>(List.of(round())));

			Page<RoundSummaryResponse> page = service.rounds(0, 10);

			assertThat(page.getContent()).hasSize(1);
			RoundSummaryResponse response = page.getContent().getFirst();
			assertThat(response.getMultigameId()).isEqualTo(START_TIME);
			assertThat(response.getParticipantCount()).isEqualTo(50);
			assertThat(response.getCapacity()).isEqualTo(5);
		}
	}

	// ---------------------------------------------------------------------
	// myRecords
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("myRecords 메서드는")
	class Describe_myRecords {

		@Test
		@DisplayName("라운드 단위로 그룹핑하여 results 배열과 함께 페이지로 반환한다")
		void it_returns_my_records_grouped_by_round() {
			given(memberRepository.findDistinctStartTimesByMemberId(eq(1L), any(Pageable.class)))
					.willReturn(new PageImpl<>(List.of(START_TIME), PageRequest.of(0, 10), 1));
			given(memberRepository.findByStartTimeInAndMemberIdOrderByStartTimeDescSubjectIdAsc(List.of(START_TIME), 1L))
					.willReturn(List.of(member()));

			Page<MyRoundRecordResponse> page = service.myRecords(1L, 0, 10);

			assertThat(page.getContent()).hasSize(1);
			MyRoundRecordResponse response = page.getContent().getFirst();
			assertThat(response.getMultigameId()).isEqualTo(START_TIME);
			assertThat(response.getResults()).hasSize(1);
			MyRoundResult result = response.getResults().getFirst();
			assertThat(result.getSubjectId()).isEqualTo(2);
			assertThat(result.getStatus()).isEqualTo("SUCCESS");
		}

		@Test
		@DisplayName("참여 라운드가 없으면 빈 페이지를 반환한다")
		void it_returns_empty_page_when_no_rounds() {
			given(memberRepository.findDistinctStartTimesByMemberId(eq(1L), any(Pageable.class)))
					.willReturn(Page.empty(PageRequest.of(0, 10)));

			Page<MyRoundRecordResponse> page = service.myRecords(1L, 0, 10);

			assertThat(page.getContent()).isEmpty();
		}
	}

	// ---------------------------------------------------------------------
	// roundDetail
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("roundDetail 메서드는")
	class Describe_roundDetail {

		@Test
		@DisplayName("라운드가 없으면 MULTIGAME_RESULT_NOT_FOUND를 던진다")
		void it_throws_when_round_absent() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.roundDetail(START_TIME, 1L))
					.isInstanceOf(BaseException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MULTIGAME_RESULT_NOT_FOUND);
		}

		@Test
		@DisplayName("과목 1~6 집계와 경쟁률을 반환하고 범위 밖 과목은 무시한다")
		void it_builds_subject_stats() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.of(round()));
			given(memberRepository.findByStartTimeAndMemberIdOrderBySubjectIdAsc(START_TIME, 1L)).willReturn(List.of());
			// {subjectId, applied, succeeded}
			given(memberRepository.aggregateBySubject(START_TIME)).willReturn(List.<Object[]>of(
					new Object[]{1, 10, 8},
					new Object[]{7, 3, 1})); // 과목 7은 스킵

			RoundDetailResponse response = service.roundDetail(START_TIME, 1L);

			assertThat(response.getMultigameId()).isEqualTo(START_TIME);
			assertThat(response.getParticipantCount()).isEqualTo(50);
			assertThat(response.getSubjects()).hasSize(6);

			RoundDetailResponse.SubjectStat subject1 = response.getSubjects().getFirst();
			assertThat(subject1.getSubjectId()).isEqualTo(1);
			assertThat(subject1.getApplied()).isEqualTo(10);
			assertThat(subject1.getSucceeded()).isEqualTo(8);
			// competitionRate = round(10 * 10 / 5) / 10 = 2.0
			assertThat(subject1.getCompetitionRate()).isEqualTo(2.0);

			RoundDetailResponse.SubjectStat subject2 = response.getSubjects().get(1);
			assertThat(subject2.getSubjectId()).isEqualTo(2);
			assertThat(subject2.getApplied()).isZero();
			assertThat(subject2.getCompetitionRate()).isZero();
		}

		@Test
		@DisplayName("좌석 수가 0이면 경쟁률을 0으로 계산한다")
		void it_returns_zero_competition_when_capacity_zero() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.of(
					MultigameRoundEntity.builder().startTime(START_TIME).participantCount(0).capacity(0).build()));
			given(memberRepository.findByStartTimeAndMemberIdOrderBySubjectIdAsc(START_TIME, 1L)).willReturn(List.of());
			given(memberRepository.aggregateBySubject(START_TIME)).willReturn(List.<Object[]>of(
					new Object[]{1, 5, 2}));

			RoundDetailResponse response = service.roundDetail(START_TIME, 1L);

			assertThat(response.getSubjects().getFirst().getCompetitionRate()).isZero();
			assertThat(response.getSubjects().getFirst().getApplied()).isEqualTo(5);
		}

		@Test
		@DisplayName("참여한 라운드면 participated=true와 내 결과 목록, 내 로그를 반환한다")
		void it_returns_my_result_and_logs_when_participated() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.of(round()));
			given(memberRepository.findByStartTimeAndMemberIdOrderBySubjectIdAsc(START_TIME, 1L))
					.willReturn(List.of(member()));
			given(memberRepository.aggregateBySubject(START_TIME)).willReturn(List.<Object[]>of(
					new Object[]{1, 10, 8}));
			given(logRepository.findByStartTimeAndMemberIdOrderByAttemptedAtAsc(START_TIME, 1L))
					.willReturn(List.of(log()));

			RoundDetailResponse response = service.roundDetail(START_TIME, 1L);

			assertThat(response.isParticipated()).isTrue();
			assertThat(response.getMyResults()).hasSize(1);
			MyRoundResult myResult = response.getMyResults().getFirst();
			assertThat(myResult.getSubjectId()).isEqualTo(2);
			assertThat(myResult.getStatus()).isEqualTo("SUCCESS");
			assertThat(response.getMyLog()).hasSize(1);
			RoundDetailResponse.AttemptLog attempt = response.getMyLog().getFirst();
			assertThat(attempt.getStatus()).isEqualTo("ENQUEUED");
			assertThat(attempt.getSubjectId()).isEqualTo(2);
			assertThat(attempt.getSeq()).isEqualTo(3);
			assertThat(attempt.getLimit()).isEqualTo(1);
		}

		@Test
		@DisplayName("미참여 라운드면 participated=false, myResults=[], myLog=[] 를 반환한다")
		void it_returns_no_my_info_when_not_participated() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.of(round()));
			given(memberRepository.findByStartTimeAndMemberIdOrderBySubjectIdAsc(START_TIME, 1L)).willReturn(List.of());
			given(memberRepository.aggregateBySubject(START_TIME)).willReturn(List.<Object[]>of());

			RoundDetailResponse response = service.roundDetail(START_TIME, 1L);

			assertThat(response.isParticipated()).isFalse();
			assertThat(response.getMyResults()).isEmpty();
			assertThat(response.getMyLog()).isEmpty();
		}
	}
}
