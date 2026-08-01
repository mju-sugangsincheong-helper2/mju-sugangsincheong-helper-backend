package com.mjusugangsincheonghelper.multigame.result.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundLogRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundRepository;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundEvent;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundSettlement;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundSettlement.MemberSubject;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoundSettlementService 단위 테스트")
class RoundSettlementServiceTest {

	private static final String START_TIME = "20260801120000";

	@Mock
	private MultigameRoundRepository roundRepository;

	@Mock
	private MultigameRoundLogRepository logRepository;

	@Mock
	private JdbcTemplate jdbcTemplate;

	private RoundSettlementService service;

	@BeforeEach
	void setUp() {
		service = new RoundSettlementService(roundRepository, logRepository, jdbcTemplate);
	}

	private RoundSettlement settlement() {
		return RoundSettlement.from(START_TIME, 2, 1, List.of(
				"1:ENQUEUED:1:1:1:0",
				"1:SUCCESS:1:2:1:1",
				"2:ENQUEUED:2:3:2:0"));
	}

	/**
	 * 두 개의 batchUpdate(멤버 업서트 / 로그 삽입)를 SQL 문자열로 구분하여
	 * 각각의 ParameterizedPreparedStatementSetter를 캡처한다.
	 */
	private void captureSetters(AtomicReference<ParameterizedPreparedStatementSetter<Map.Entry<MemberSubject, RoundEvent>>> memberSetter,
			AtomicReference<ParameterizedPreparedStatementSetter<RoundEvent>> eventSetter) {
		doAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			if (sql.contains("multigame_round_member")) {
				memberSetter.set(invocation.getArgument(3));
			} else if (sql.contains("multigame_round_log")) {
				eventSetter.set(invocation.getArgument(3));
			}
			return new int[0][];
		}).when(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());
	}

	// ---------------------------------------------------------------------
	// save: 라운드 메타 Upsert
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("save 메서드는")
	class Describe_save {

		@Test
		@DisplayName("라운드가 없으면 새로 저장한다")
		void it_saves_new_round() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.empty());

			service.save(settlement());

			ArgumentCaptor<MultigameRoundEntity> captor = ArgumentCaptor.forClass(MultigameRoundEntity.class);
			verify(roundRepository).save(captor.capture());
			assertThat(captor.getValue().getStartTime()).isEqualTo(START_TIME);
			assertThat(captor.getValue().getParticipantCount()).isEqualTo(2);
			assertThat(captor.getValue().getCapacity()).isEqualTo(1);
		}

		@Test
		@DisplayName("라운드가 이미 있으면 기존 레코드를 갱신한다")
		void it_updates_existing_round() {
			MultigameRoundEntity existing = MultigameRoundEntity.builder()
					.startTime(START_TIME)
					.participantCount(1)
					.capacity(1)
					.build();
			given(roundRepository.findById(START_TIME)).willReturn(Optional.of(existing));

			service.save(settlement());

			verify(roundRepository, never()).save(any(MultigameRoundEntity.class));
			assertThat(existing.getParticipantCount()).isEqualTo(2);
			assertThat(existing.getCapacity()).isEqualTo(1);
		}

		@Test
		@DisplayName("유저별 최종 상태 Map을 멤버 업서트 배치에 넘긴다")
		void it_batches_member_upserts() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.empty());

			service.save(settlement());

			ArgumentCaptor<Collection<?>> membersCaptor = ArgumentCaptor.forClass(Collection.class);
			verify(jdbcTemplate).batchUpdate(argThat(sql -> sql.contains("multigame_round_member")), membersCaptor.capture(), anyInt(), any());
			assertThat(membersCaptor.getValue()).hasSize(2);
		}

		@Test
		@DisplayName("같은 유저가 과목별로 여러 레코드를 가지면 각각 업서트한다")
		void it_batches_multi_subject_member_upserts() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.empty());
			RoundSettlement settlement = RoundSettlement.from(START_TIME, 2, 1, List.of(
					"1:SUCCESS:1:1:1:1", "1:SUCCESS:2:2:2:1", "2:ENQUEUED:3:3:3:0"));

			service.save(settlement);

			ArgumentCaptor<Collection<?>> membersCaptor = ArgumentCaptor.forClass(Collection.class);
			verify(jdbcTemplate).batchUpdate(argThat(sql -> sql.contains("multigame_round_member")), membersCaptor.capture(), anyInt(), any());
			assertThat(membersCaptor.getValue()).hasSize(3);
		}

		@Test
		@DisplayName("멤버 업서트는 최종 상태와 과목별 키를 바인딩한다")
		void it_binds_member_upsert_parameters() throws Exception {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.empty());
			RoundSettlement settlement = settlement();
			AtomicReference<ParameterizedPreparedStatementSetter<Map.Entry<MemberSubject, RoundEvent>>> memberSetter = new AtomicReference<>();
			AtomicReference<ParameterizedPreparedStatementSetter<RoundEvent>> eventSetter = new AtomicReference<>();
			captureSetters(memberSetter, eventSetter);

			service.save(settlement);

			assertThat(memberSetter.get()).isNotNull();
			PreparedStatement ps = mock(PreparedStatement.class);

			Map.Entry<MemberSubject, RoundEvent> successEntry = settlement.finalMembers().entrySet().stream()
					.filter(entry -> entry.getKey().memberId() == 1L)
					.findFirst()
					.orElseThrow();
			memberSetter.get().setValues(ps, successEntry);
			verify(ps).setString(1, START_TIME);
			verify(ps).setLong(2, 1L);
			verify(ps).setInt(3, successEntry.getKey().subjectId()); // SUCCESS 유저의 subjectId
			verify(ps).setString(4, "SUCCESS");

			Map.Entry<MemberSubject, RoundEvent> queuedEntry = settlement.finalMembers().entrySet().stream()
					.filter(entry -> entry.getKey().memberId() == 2L)
					.findFirst()
					.orElseThrow();
			memberSetter.get().setValues(ps, queuedEntry);
			verify(ps).setString(4, "FAIL_SOLDOUT"); // 큐에 남은 유저는 FAIL_SOLDOUT
		}

		@Test
		@DisplayName("기존 로그를 삭제한 뒤 전체 이벤트를 배치 삽입한다")
		void it_deletes_then_batches_logs() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.empty());

			service.save(settlement());

			ArgumentCaptor<Collection<?>> eventsCaptor = ArgumentCaptor.forClass(Collection.class);
			verify(jdbcTemplate).batchUpdate(argThat(sql -> sql.contains("multigame_round_log")), eventsCaptor.capture(), anyInt(), any());
			assertThat(eventsCaptor.getValue()).hasSize(3);
			verify(logRepository).deleteAllByStartTime(START_TIME);
		}

		@Test
		@DisplayName("로그 삽입 파라미터를 바인딩한다")
		void it_binds_event_parameters() throws Exception {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.empty());
			RoundSettlement settlement = settlement();
			AtomicReference<ParameterizedPreparedStatementSetter<Map.Entry<MemberSubject, RoundEvent>>> memberSetter = new AtomicReference<>();
			AtomicReference<ParameterizedPreparedStatementSetter<RoundEvent>> eventSetter = new AtomicReference<>();
			captureSetters(memberSetter, eventSetter);

			service.save(settlement);

			assertThat(eventSetter.get()).isNotNull();
			PreparedStatement ps = mock(PreparedStatement.class);

			RoundEvent event = settlement.events().getFirst();
			eventSetter.get().setValues(ps, event);

			verify(ps).setString(1, START_TIME);
			verify(ps).setLong(2, event.memberId());
			verify(ps).setInt(3, event.subjectId());
			verify(ps).setString(4, event.status());
			verify(ps).setLong(5, event.sequence());
			verify(ps).setInt(6, event.limit());
			verify(ps).setTimestamp(7, Timestamp.from(event.attemptedAt()));
		}

		@Test
		@DisplayName("멤버 업서트 → 로그 삭제 → 로그 삽입 순서로 실행한다")
		void it_executes_in_order() {
			given(roundRepository.findById(START_TIME)).willReturn(Optional.empty());

			service.save(settlement());

			InOrder inOrder = inOrder(jdbcTemplate, logRepository);
			inOrder.verify(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());
			inOrder.verify(logRepository).deleteAllByStartTime(START_TIME);
			inOrder.verify(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());
		}
	}
}
