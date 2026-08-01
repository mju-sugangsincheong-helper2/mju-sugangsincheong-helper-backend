package com.mjusugangsincheonghelper.multigame.game.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RoundTime {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private RoundTime() {
	}

	public static String target(LocalDateTime now) {
		int remainder = now.getMinute() % 10;
		LocalDateTime mark = now.withMinute(now.getMinute() - remainder).withSecond(0).withNano(0);
		return (remainder <= 1 ? mark : mark.plusMinutes(10)).format(FORMATTER);
	}

	public static String currentMark(LocalDateTime now) {
		return now.withMinute(now.getMinute() - now.getMinute() % 10)
				.withSecond(0)
				.withNano(0)
				.format(FORMATTER);
	}

	public static LocalDateTime parse(String value) {
		return LocalDateTime.parse(value, FORMATTER);
	}
}
