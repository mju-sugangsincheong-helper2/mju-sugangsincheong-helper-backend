package com.mjusugangsincheonghelper.multigame.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class GameTimeCalculator {

	private GameTimeCalculator() {
	}

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	public static String computeNextT(LocalDateTime now) {
		int minute = now.getMinute();
		int tenMark = (minute / 10) * 10;
		int minutesToAdd = 10 - (minute - tenMark);
		return now.plusMinutes(minutesToAdd).withSecond(0).withNano(0).format(FORMATTER);
	}

	public static String computeCurrentT(LocalDateTime now) {
		int minute = now.getMinute();
		int tenMark = (minute / 10) * 10;
		return now.withMinute(tenMark).withSecond(0).withNano(0).format(FORMATTER);
	}

	public static LocalDateTime parseT(String t) {
		return LocalDateTime.parse(t, FORMATTER);
	}

	public static String formatT(LocalDateTime dateTime) {
		return dateTime.format(FORMATTER);
	}
}
