package com.mjusugangsincheonghelper.multigame.result.domain;

import java.time.Instant;

public record RoundEvent(long memberId, String status, int subjectId, Instant attemptedAt, long sequence, int limit) {

	public static RoundEvent parse(String value) {
		String[] fields = value.split(":", -1);
		if (fields.length != 6) {
			throw new IllegalArgumentException("Invalid multigame event");
		}
		return new RoundEvent(Long.parseLong(fields[0]), fields[1], Integer.parseInt(fields[2]),
				Instant.ofEpochMilli(Long.parseLong(fields[3])), Long.parseLong(fields[4]), Integer.parseInt(fields[5]));
	}
}
