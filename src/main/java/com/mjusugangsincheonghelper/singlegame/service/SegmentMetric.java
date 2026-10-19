package com.mjusugangsincheonghelper.singlegame.service;

/**
 * 구간 시도(SegmentAttempt)의 측정 지표. DB 구간 분위수표의 컬럼 블록과 1:1 대응.
 */
public enum SegmentMetric {
	/** 과목 조준 지연 (t_click_course) */
	AIM,
	/** 신청 확인 지연 (t_click_yes) */
	CONFIRM,
	/** 완료 확인 지연 (t_click_ok) */
	COMPLETE,
	/** 구간 합계 파생값 (AIM + CONFIRM + COMPLETE). 저장 안함, 분포만 조회 */
	TOTAL
}
