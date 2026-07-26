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

	/**
	 * 현재 시각이 속한 액티브 게임의 T를 반환합니다.
	 * <p>
	 * 각 게임 T의 윈도우는 [T-5m, T+5m)입니다.
	 * <ul>
	 *   <li>분이 5 이상 (minute % 10 >= 5): 윈도우 전반부 → 다음 10분 마크 (ceiling)</li>
	 *   <li>분이 5 미만 (minute % 10 < 5): 윈도우 후반부 → 현재 10분 마크 (floor)</li>
	 * </ul>
	 * <p>
	 * 예: 12:07 → T=12:10, 12:10 → T=12:10, 12:15 → T=12:20
	 */
	public static String computeActiveGameT(LocalDateTime now) {
		if (now.getMinute() % 10 >= 5) {
			// [T-5m, T) 구간: ceiling 사용
			return computeNextT(now);
		} else {
			// [T, T+5m) 구간: floor 사용
			return computeCurrentT(now);
		}
	}

	public static LocalDateTime parseT(String t) {
		return LocalDateTime.parse(t, FORMATTER);
	}

	public static String formatT(LocalDateTime dateTime) {
		return dateTime.format(FORMATTER);
	}
}
