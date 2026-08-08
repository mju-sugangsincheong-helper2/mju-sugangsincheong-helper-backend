package com.mjusugangsincheonghelper.singlegame.dto.cache;

import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 싱글게임 통계 데이터 묶음.
 *
 * <p>totalCourses(및 선택적 dept) 단위로 캐시된다. 키 공간이
 * totalCourses 종류(5개) × (1 + 학과 수)로 유계이므로 TTL만으로
 * 메모리 관리가 가능하다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsBundle implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 시퀀스별 퍼센타일 (cc/cy/cok/total × p10/p30/p50/p70 = 16 values per seq) */
	private Map<Integer, double[]> seqPercentileStats;

	/** 진입 시간 퍼센타일 [p10, p30, p50, p70] */
	private double[] enterMainPercentileStats;

	/** 게임별 집계 [avgCC, avgCY, avgCOK, avgBurst, t1Total, paceStddev, initialSprint, fatigueIndex] */
	private java.util.List<double[]> aggregates;
}
