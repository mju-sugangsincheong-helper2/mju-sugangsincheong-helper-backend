package com.mjusugangsincheonghelper.singlegame.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 싱글게임 이벤트별 반응시간(min/max) 설정.
 * yml의 {@code app.singlegame.timing} 하위 항목으로 바인딩된다.
 * (relaxed binding 덕분에 {@code t-enter-main}, {@code min-ms} 등 snake/kebab 표기 모두 허용)
 *
 * <pre>
 * app:
 *   singlegame:
 *     timing:
 *       t-enter-main:   { min-ms: 1, max-ms: 60000 }  # 메인방 입장 반응시간
 *       t-click-course: { min-ms: 1, max-ms: 60000 }  # 과목 조준/클릭 시간
 *       t-click-yes:    { min-ms: 1, max-ms: 60000 }  # 1차 확인 팝업 반응시간
 *       t-click-ok:     { min-ms: 1, max-ms: 60000 }  # 2차 완료 팝업 반응시간
 * </pre>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.singlegame")
public class SingleGameProperties {

	/** 이벤트별 반응시간 범위 설정. */
	private Timing timing = new Timing();

	@Getter
	@Setter
	public static class Timing {
		/** 메인방 진입 반응 시간 (t_enter_main). 전체 1회 측정. */
		private EventTiming tEnterMain = new EventTiming();

		/** 과목 조준/클릭 시간 (t_click_course). 과목별 1회 측정. */
		private EventTiming tClickCourse = new EventTiming();

		/** 1차 확인 팝업("신청하시겠습니까?") 반응 시간 (t_click_yes). 과목별 1회 측정. */
		private EventTiming tClickYes = new EventTiming();

		/** 2차 완료 팝업("수강 신청 되었습니다") 반응 시간 (t_click_ok). 과목별 1회 측정. */
		private EventTiming tClickOk = new EventTiming();
	}

	/** 단일 이벤트의 최소/최대 반응 시간(ms). */
	@Getter
	@Setter
	public static class EventTiming {

		/** 최소 반응 시간 (ms). */
		private int minMs = 1;

		/** 최대 반응 시간 (ms). */
		private int maxMs = 60000;
	}
}