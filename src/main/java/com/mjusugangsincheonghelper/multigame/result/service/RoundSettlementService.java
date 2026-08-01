package com.mjusugangsincheonghelper.multigame.result.service;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundLogRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundRepository;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundEvent;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundSettlement;
import com.mjusugangsincheonghelper.multigame.result.domain.RoundSettlement.MemberSubject;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoundSettlementService {

	private final MultigameRoundRepository roundRepository;
	private final MultigameRoundLogRepository logRepository;
	private final JdbcTemplate jdbcTemplate;

	@Transactional
	public void save(RoundSettlement settlement) {
		roundRepository.findById(settlement.startTime()).ifPresentOrElse(
				round -> round.update(settlement.participantCount(), settlement.capacity()),
				() -> roundRepository.save(MultigameRoundEntity.builder()
						.startTime(settlement.startTime())
						.participantCount(settlement.participantCount())
						.capacity(settlement.capacity())
						.build()));
		roundRepository.flush();

		jdbcTemplate.batchUpdate("""
				INSERT INTO multigame_round_member (start_time, member_id, subject_id, status, created_at)
				VALUES (?, ?, ?, ?, now())
				ON CONFLICT (start_time, member_id, subject_id)
				DO UPDATE SET status = EXCLUDED.status
				""", settlement.finalMembers().entrySet(), 100,
				(statement, entry) -> bindMember(statement, settlement.startTime(), entry.getKey(), entry.getValue(), settlement));

		logRepository.deleteAllByStartTime(settlement.startTime());
		jdbcTemplate.batchUpdate("""
				INSERT INTO multigame_round_log
				(start_time, member_id, subject_id, attempt_status, attempt_seq, current_limit, attempted_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", settlement.events(), 100, (statement, event) -> bindEvent(statement, settlement.startTime(), event));
	}

	private void bindEvent(java.sql.PreparedStatement statement, String startTime, RoundEvent event) throws java.sql.SQLException {
		statement.setString(1, startTime);
		statement.setLong(2, event.memberId());
		statement.setInt(3, event.subjectId());
		statement.setString(4, event.status());
		statement.setLong(5, event.sequence());
		statement.setInt(6, event.limit());
		statement.setTimestamp(7, Timestamp.from(event.attemptedAt()));
	}

	private void bindMember(java.sql.PreparedStatement statement, String startTime, MemberSubject key,
			RoundEvent event, RoundSettlement settlement) throws java.sql.SQLException {
		statement.setString(1, startTime);
		statement.setLong(2, key.memberId());
		statement.setInt(3, key.subjectId());
		statement.setString(4, settlement.finalStatus(event));
	}
}
