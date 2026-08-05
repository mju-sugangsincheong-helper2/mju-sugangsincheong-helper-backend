package com.mjusugangsincheonghelper.multigame.result.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundLogEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 라운드 상세. 게임 시작 시각(multigameId = T), 참여자 수(participantCount),
 * 과목당 배정된 정원(capacity), 내가 참여한 판인지(participated), 그리고 서버에 기록된
 * 전체 처리 시계열(timeline)만 노출한다. 시계열에는 항상 전체 기록(익명)이 담기며,
 * 참여자는 실제 ID 대신 등장 순서대로 부여된 번호(participantNo 1, 2, 3...)로 구분되고,
 * 참여한 판이면 내 기록도 함께 포함된다(mine=true).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundDetailResponse {
	private String multigameId;
	private int participantCount;
	private int capacity;
	private boolean participated;
	private List<TimelineEntry> timeline;

	public static RoundDetailResponse from(MultigameRoundEntity round, List<MultigameRoundLogEntity> timelineLogs,
			long memberId) {
		Map<Long, Integer> participantNumbers = new HashMap<>();
		List<TimelineEntry> timeline = new ArrayList<>();
		int nextNumber = 1;
		for (MultigameRoundLogEntity log : timelineLogs) {
			Integer participantNo = participantNumbers.get(log.getMemberId());
			if (participantNo == null) {
				participantNo = nextNumber++;
				participantNumbers.put(log.getMemberId(), participantNo);
			}
			timeline.add(TimelineEntry.from(log, participantNo, log.getMemberId() == memberId));
		}
		boolean participated = participantNumbers.containsKey(memberId);
		return RoundDetailResponse.builder()
				.multigameId(round.getStartTime())
				.participantCount(round.getParticipantCount())
				.capacity(round.getCapacity())
				.participated(participated)
				.timeline(timeline)
				.build();
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TimelineEntry {
		private int participantNo;
		private int subjectId;
		private String status;
		private long seq;
		private int limit;
		private Instant attemptedAt;
		private boolean mine;

		public static TimelineEntry from(MultigameRoundLogEntity log, int participantNo, boolean mine) {
			return TimelineEntry.builder()
					.participantNo(participantNo)
					.subjectId(log.getSubjectId())
					.status(log.getAttemptStatus())
					.seq(log.getAttemptSeq())
					.limit(log.getCurrentLimit())
					.attemptedAt(log.getAttemptedAt())
					.mine(mine)
					.build();
		}
	}
}
