package com.mjusugangsincheonghelper.multigame.game.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;

/**
 * 멀티게임 라이프사이클 상태를 시각 기반으로 판정한다.
 *
 * <p>모든 판정은 {@link Instant}(UTC)를 입력으로 받으므로 JVM 시간대에 영향받지 않는다.
 * 미운영(CLOSED) 시간대는 {@link OffsetTime}(명시 오프셋, 예: {@code 02:00:00+09:00})으로
 * 설정되며, {@code now}를 해당 오프셋 벽시계로 변환해 비교한다. 회차/페이즈 경계(분/초)는
 * 한국 오프셋이 정수 시간이므로 UTC 벽시계와 동일하며 {@link ZoneOffset#UTC}로 산출한다.
 */
public final class GameStatusResolver {

	/** 기본 미운영 시간대: 한국 표준시 02:00 ~ 05:00. yml 미설정 시 사용된다. */
	private static final OffsetTime DEFAULT_CLOSED_START = OffsetTime.of(2, 0, 0, 0, ZoneOffset.ofHours(9));
	private static final OffsetTime DEFAULT_CLOSED_END = OffsetTime.of(5, 0, 0, 0, ZoneOffset.ofHours(9));

	private final OffsetTime closedStart;
	private final OffsetTime closedEnd;

	/** 기본 설정(한국 표준시 02:00 ~ 05:00)으로 생성. 테스트 또는 yml 미설정 시 사용된다. */
	public GameStatusResolver() {
		this(DEFAULT_CLOSED_START, DEFAULT_CLOSED_END);
	}

	/** 미운영 시간대를 외부 설정(yml의 {@code app.multigame.start-close/end-close})으로 주입한다. */
	public GameStatusResolver(OffsetTime closedStart, OffsetTime closedEnd) {
		this.closedStart = closedStart;
		this.closedEnd = closedEnd;
	}

	public GameStatus resolve(Instant now, RuntimeState runtimeState) {
		if (isClosed(now)) {
			return GameStatus.CLOSED;
		}

		LocalDateTime t = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
		int remainder = t.getMinute() % 10;
		if (remainder > 0) {
			return secondsToNextMark(t) <= 5
					? readyStatus(runtimeState)
					: GameStatus.WAITING;
		}

		if (t.getSecond() < 30) {
			return progressStatus(runtimeState);
		}
		return GameStatus.ENDED;
	}

	/** 미운영(CLOSED) 시간대인지 판정한다. 스케줄러 등 게임 상태가 아닌 시간대만 필요한 곳에서도 재사용한다. */
	public boolean isClosed(Instant now) {
		if (closedStart.equals(closedEnd)) {
			return false; // start-close == end-close 이면 미운영 시간대 없음 (24시간 운영)
		}
		OffsetTime time = OffsetTime.ofInstant(now, closedStart.getOffset());
		OffsetTime end = closedEnd.withOffsetSameInstant(closedStart.getOffset());
		if (closedStart.isBefore(end)) {
			return !time.isBefore(closedStart) && time.isBefore(end);
		}
		// 자정을 넘기는 설정 (예: 23:00+09:00 ~ 01:00+09:00) — 시작 이후 자정까지, 또는 자정부터 종료 전까지 CLOSED
		return !time.isBefore(closedStart) || time.isBefore(end);
	}

	private int secondsToNextMark(LocalDateTime now) {
		return (10 - now.getMinute() % 10) * 60 - now.getSecond();
	}

	private GameStatus readyStatus(RuntimeState state) {
		if (state == RuntimeState.CANCELLED) {
			return GameStatus.CANCELLED;
		}
		return state == RuntimeState.READY ? GameStatus.READY : GameStatus.STARTING;
	}

	private GameStatus progressStatus(RuntimeState state) {
		if (state == RuntimeState.CANCELLED) {
			return GameStatus.CANCELLED;
		}
		return state == RuntimeState.PROGRESS ? GameStatus.PROGRESS : GameStatus.STARTING;
	}
}
