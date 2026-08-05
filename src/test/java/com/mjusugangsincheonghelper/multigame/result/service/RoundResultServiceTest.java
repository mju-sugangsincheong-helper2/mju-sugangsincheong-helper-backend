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

	private MultigameRoundLogEntity logOf(long memberId) {
		return MultigameRoundLogEntity.builder()
				.startTime(START_TIME)
				.memberId(memberId)
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
		}
	}

	// ---------------------------------------------------------------------
	// myRecords
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("myRecords 메서드는")
	class Describe_myRecords {

		@Test
		@DisplayName("라운드 단위로 그룹핑하여 참여자 수와 성공 과목 수를 함께 반환한다")
		void it_returns_my_records_grouped_by_round() {
			given(memberRepository.findDistinctStartTimesByMemberId(eq(1L), any(Pageable.class)))
					.willReturn(new PageImpl<>(List.of(START_TIME), PageRequest.of(0, 10), 1));
			given(memberRepository.findByStartTimeInAndMemberIdOrderByStartTimeDescSubjectIdAsc(List.of(START_TIME), 1L))
					.willReturn(List.of(member()));
			given(roundRepository.findAllById(List.of(START_TIME))).willReturn(List.of(round()));

			Page<MyRoundRecordResponse> page = service.myRecords(1L, 0, 10);

			assertThat(page.getContent()).hasSize(1);
			MyRoundRecordResponse response = page.getContent().getFirst();
			assertThat(response.getMultigameId()).isEqualTo(START_TIME);
			assertThat(response.getParticipantCount()).isEqualTo(50);
			assertThat(response.getSuccessCount()).isEqualTo(1);
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
		@DisplayName("참여한 판이면 participated=true와 함께 내 기록이 mine=true로 표시된 전체 시계열을 반환한다")
		void it_returns_full_timeline_with_my_entries_marked() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.of(round()));
			MultigameRoundLogEntity otherLog = MultigameRoundLogEntity.builder()
					.startTime(START_TIME)
					.memberId(2L)
					.subjectId(3)
					.attemptStatus("SUCCESS")
					.attemptSeq(4L)
					.currentLimit(2)
					.attemptedAt(Instant.parse("2026-08-01T12:00:02Z"))
					.build();
			MultigameRoundLogEntity mySecondLog = MultigameRoundLogEntity.builder()
					.startTime(START_TIME)
					.memberId(1L)
					.subjectId(4)
					.attemptStatus("FAIL_SOLDOUT")
					.attemptSeq(5L)
					.currentLimit(3)
					.attemptedAt(Instant.parse("2026-08-01T12:00:03Z"))
					.build();
			given(logRepository.findByStartTimeOrderByAttemptedAtAsc(START_TIME))
					.willReturn(List.of(logOf(1L), otherLog, mySecondLog));

			RoundDetailResponse response = service.roundDetail(START_TIME, 1L);

			assertThat(response.getMultigameId()).isEqualTo(START_TIME);
			assertThat(response.getParticipantCount()).isEqualTo(50);
			assertThat(response.getCapacity()).isEqualTo(5);
			assertThat(response.isParticipated()).isTrue();
			assertThat(response.getTimeline()).hasSize(3);

			// 등장 순서대로 participantNo가 부여되며, 같은 참여자는 동일한 번호를 유지한다.
			RoundDetailResponse.TimelineEntry mine = response.getTimeline().getFirst();
			assertThat(mine.getParticipantNo()).isEqualTo(1);
			assertThat(mine.isMine()).isTrue();
			assertThat(mine.getSubjectId()).isEqualTo(2);
			assertThat(mine.getStatus()).isEqualTo("ENQUEUED");
			assertThat(mine.getSeq()).isEqualTo(3);
			assertThat(mine.getLimit()).isEqualTo(1);

			RoundDetailResponse.TimelineEntry other = response.getTimeline().get(1);
			assertThat(other.getParticipantNo()).isEqualTo(2);
			assertThat(other.isMine()).isFalse();
			assertThat(other.getSubjectId()).isEqualTo(3);
			assertThat(other.getStatus()).isEqualTo("SUCCESS");

			RoundDetailResponse.TimelineEntry mineAgain = response.getTimeline().get(2);
			assertThat(mineAgain.getParticipantNo()).isEqualTo(1);
			assertThat(mineAgain.isMine()).isTrue();
		}

		@Test
		@DisplayName("미참여 판이면 participated=false이고 전체 시계열의 mine이 모두 false다")
		void it_returns_full_timeline_without_my_entries_when_not_participated() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.of(round()));
			given(logRepository.findByStartTimeOrderByAttemptedAtAsc(START_TIME))
					.willReturn(List.of(logOf(2L)));

			RoundDetailResponse response = service.roundDetail(START_TIME, 1L);

			assertThat(response.isParticipated()).isFalse();
			assertThat(response.getTimeline()).hasSize(1);
			assertThat(response.getTimeline().getFirst().isMine()).isFalse();
		}
	}
}
