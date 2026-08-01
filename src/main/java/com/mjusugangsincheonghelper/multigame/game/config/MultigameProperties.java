package com.mjusugangsincheonghelper.multigame.game.config;

import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 멀티게임 라이프사이클 설정. yml의 {@code app.multigame} 하위 항목으로 바인딩된다.
 * (relaxed binding 덕분에 {@code start-close}, {@code start_close}, {@code startClose} 모두 허용)
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.multigame")
public class MultigameProperties {

	/** 미운영(CLOSED) 시간대 시작 시각. 이 시각부터 게임이 생성되지 않는다. */
	private LocalTime startClose = LocalTime.of(2, 0);

	/** 미운영(CLOSED) 시간대 종료 시각. 이 시각 전까지 CLOSED로 판정된다. */
	private LocalTime endClose = LocalTime.of(5, 0);
}
