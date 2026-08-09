package com.mjusugangsincheonghelper.multigame.game.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 멀티게임 회차 식별자(multigameId)를 UTC 벽시계 기준으로 산출/파싱한다.
 *
 * <p>회차 식별자는 {@code yyyyMMddHHmmss} 14자리 문자열이며, 그 값은 해당 회차 시작 순간의
 * <strong>UTC</strong> 벽시계를 인코딩한다. JVM 시간대와 무관하게 항상 UTC로 산출되므로
 * 시간대 해석의 애매함이 없다. (한국은 UTC+9 고정 오프셋, DST 없음)
 */
public final class RoundTime {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private RoundTime() {
	}

	/** now 기준 타겟 회차 식별자를 UTC 벽시계로 산출한다. */
	public static String target(Instant now) {
		LocalDateTime t = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
		int remainder = t.getMinute() % 10;
		LocalDateTime mark = t.withMinute(t.getMinute() - remainder).withSecond(0).withNano(0);
		return (remainder <= 1 ? mark : mark.plusMinutes(10)).format(FORMATTER);
	}

	/** now가 속한 10분 마크(회차 식별자)를 UTC 벽시계로 반환한다. */
	public static String currentMark(Instant now) {
		LocalDateTime t = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
		return t.withMinute(t.getMinute() - t.getMinute() % 10)
				.withSecond(0)
				.withNano(0)
				.format(FORMATTER);
	}

	/** 14자리 회차 식별자(UTC 벽시계)를 {@link Instant}로 파싱한다. */
	public static Instant parse(String value) {
		return LocalDateTime.parse(value, FORMATTER).toInstant(ZoneOffset.UTC);
	}
}
