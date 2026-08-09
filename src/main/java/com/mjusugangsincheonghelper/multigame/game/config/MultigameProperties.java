package com.mjusugangsincheonghelper.multigame.game.config;

import java.time.Duration;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 멀티게임 라이프사이클 및 스케줄 설정. yml의 {@code app.multigame} 하위 항목으로 바인딩된다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.multigame")
public class MultigameProperties {

	/**
	 * 미운영(CLOSED) 시간대 시작 시각(명시 오프셋). yml에는 ISO-8601 형식
	 * (예: {@code 02:00:00+09:00})으로 적는다. 이 시각부터 게임이 생성되지 않는다.
	 */
	private OffsetTime startClose = OffsetTime.of(2, 0, 0, 0, ZoneOffset.ofHours(9));

	/**
	 * 미운영(CLOSED) 시간대 종료 시각(명시 오프셋). yml에는 ISO-8601 형식
	 * (예: {@code 05:00:00+09:00})으로 적는다. 이 시각 전까지 CLOSED로 판정된다.
	 */
	private OffsetTime endClose = OffsetTime.of(5, 0, 0, 0, ZoneOffset.ofHours(9));

	/** 비동기 공급(Ramp-Up) 딜레이 및 반복 설정. */
	private Supply supply = new Supply();

	/** 게임 라이프사이클 크론 스케줄 설정. */
	private Schedule schedule = new Schedule();

	@Getter
	@Setter
	public static class Schedule {
		private String gameReadyCron = "55 9/10 * * * *";
		private String gameStartCron = "0 0/10 * * * *";
		private String gameFinishCron = "30 0/10 * * * *";
	}

	@Getter
	@Setter
	public static class Supply {

		/** 공급 램프업 전체 수행 지연 시간. */
		private Duration totalRampUpDuration = Duration.ofSeconds(30);

		/** 램프업 반복 루프 스텝당 지연 대기 시간. */
		private Duration stepSleepInterval = Duration.ofSeconds(1);
	}
}

