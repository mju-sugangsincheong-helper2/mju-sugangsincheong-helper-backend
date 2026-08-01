package com.mjusugangsincheonghelper.multigame.result.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record RoundSettlement(String startTime, int participantCount, int capacity, List<RoundEvent> events,
		Map<MemberSubject, RoundEvent> finalMembers) {

	public static RoundSettlement from(String startTime, int participantCount, int capacity, List<String> rawEvents) {
		List<RoundEvent> events = rawEvents.stream().map(RoundEvent::parse).toList();
		Map<MemberSubject, RoundEvent> members = events.stream().collect(Collectors.toMap(
				event -> new MemberSubject(event.memberId(), event.subjectId()),
				Function.identity(), BinaryOperatorByPriority.INSTANCE));
		return new RoundSettlement(startTime, participantCount, capacity, events, members);
	}

	public String finalStatus(RoundEvent event) {
		return "SUCCESS".equals(event.status()) ? "SUCCESS" : "FAIL_SOLDOUT";
	}

	/**
	 * 유저별 최종 결과를 (memberId, subjectId) 단위로 집계하기 위한 키.
	 * 한 라운드에서 과목별로 각각 성공할 수 있으므로, 같은 유저가 과목 수만큼 여러 레코드를 가질 수 있다.
	 */
	public record MemberSubject(long memberId, int subjectId) {
	}

	private enum BinaryOperatorByPriority implements java.util.function.BinaryOperator<RoundEvent> {
		INSTANCE;

		@Override
		public RoundEvent apply(RoundEvent left, RoundEvent right) {
			int comparison = Comparator.comparingInt((RoundEvent event) -> priority(event.status()))
					.thenComparing(RoundEvent::attemptedAt)
					.compare(left, right);
			return comparison >= 0 ? left : right;
		}

		private static int priority(String status) {
			return switch (status) {
				case "SUCCESS" -> 4;
				case "FAIL_SOLDOUT" -> 3;
				case "FAIL_DUPLICATE" -> 2;
				default -> 1;
			};
		}
	}
}
