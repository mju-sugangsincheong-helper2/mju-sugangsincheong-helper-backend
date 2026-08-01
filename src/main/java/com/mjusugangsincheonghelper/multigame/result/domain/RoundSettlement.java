package com.mjusugangsincheonghelper.multigame.result.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record RoundSettlement(String startTime, int participantCount, int capacity, List<RoundEvent> events,
		Map<Long, RoundEvent> finalMembers) {

	public static RoundSettlement from(String startTime, int participantCount, int capacity, List<String> rawEvents) {
		List<RoundEvent> events = rawEvents.stream().map(RoundEvent::parse).toList();
		Map<Long, RoundEvent> members = events.stream().collect(Collectors.toMap(RoundEvent::memberId,
				Function.identity(), BinaryOperatorByPriority.INSTANCE));
		return new RoundSettlement(startTime, participantCount, capacity, events, members);
	}

	public String finalStatus(RoundEvent event) {
		return "SUCCESS".equals(event.status()) ? "SUCCESS" : "FAIL_SOLDOUT";
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
