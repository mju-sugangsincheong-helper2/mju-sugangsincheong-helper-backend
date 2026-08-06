package com.mjusugangsincheonghelper.multigame.game.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;

public final class GameStatusResolver {

	private static final LocalTime DEFAULT_CLOSED_START = LocalTime.of(2, 0);
	private static final LocalTime DEFAULT_CLOSED_END = LocalTime.of(5, 0);

	private final LocalTime closedStart;
	private final LocalTime closedEnd;

	/** 기본 설정(02:00 ~ 05:00)으로 생성. 테스트 또는 yml 미설정 시 사용된다. */
	public GameStatusResolver() {
		this(DEFAULT_CLOSED_START, DEFAULT_CLOSED_END);
	}

	/** 미운영 시간대를 외부 설정(yml의 {@code app.multigame.start-close/end-close})으로 주입한다. */
	public GameStatusResolver(LocalTime closedStart, LocalTime closedEnd) {
		this.closedStart = closedStart;
		this.closedEnd = closedEnd;
	}

	public GameStatus resolve(LocalDateTime now, RuntimeState runtimeState) {
		if (isClosed(now)) {
			return GameStatus.CLOSED;
		}

		int remainder = now.getMinute() % 10;
		if (remainder > 0) {
			return secondsToNextMark(now) <= 5
					? readyStatus(runtimeState)
					: GameStatus.WAITING;
		}

		if (now.getSecond() < 30) {
			return progressStatus(runtimeState);
		}
		return GameStatus.ENDED;
	}

	/** 미운영(CLOSED) 시간대인지 판정한다. 스케줄러 등 게임 상태가 아닌 시간대만 필요한 곳에서도 재사용한다. */
	public boolean isClosed(LocalDateTime now) {
		LocalTime time = now.toLocalTime();
		if (closedStart.equals(closedEnd)) {
			return false; // start-close == end-close 이면 미운영 시간대 없음 (24시간 운영)
		}
		if (closedStart.isBefore(closedEnd)) {
			return !time.isBefore(closedStart) && time.isBefore(closedEnd);
		}
		// 자정을 넘기는 설정 (예: 23:00 ~ 01:00) — 시작 이후 자정까지, 또는 자정부터 종료 전까지 CLOSED
		return !time.isBefore(closedStart) || time.isBefore(closedEnd);
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
