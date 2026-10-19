package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import com.mjusugangsincheonghelper.singlegame.dto.cache.FeedbackQuantileTable;
import com.mjusugangsincheonghelper.singlegame.dto.cache.QuantileBand;
import com.mjusugangsincheonghelper.singlegame.dto.cache.SequenceQuantileTable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 싱글게임 통계 계산 전담 서비스.
 *
 * <p>용어: 분위수(quantileMs, Q05~Q95)=DB 원천, 백분위순위(percentileRank)=보간 근사.
 * 캐시하는 값은 전부 "최종 답" 크기다. 게임별 원시 리스트를 캐시하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SingleGameStatsService {

	private final SingleGameRepository singleGameRepository;
	private final CacheManager cacheManager;

	/** 키 포맷 단일 원천. @Cacheable SpEL(T(...))와 evict가 공유한다. */
	public static String headCountKey(int totalCourses) {
		return totalCourses + ":counts:cache";
	}

	public static String deptHeadCountKey(int totalCourses, String department) {
		return totalCourses + ":dept:" + department + ":counts:cache";
	}

	public static String quantilesKey(int totalCourses) {
		return totalCourses + ":quantiles:cache";
	}

	public static String deptQuantilesKey(int totalCourses, String department) {
		return totalCourses + ":dept:" + department + ":quantiles:cache";
	}

	public static String feedbackQuantilesKey(int totalCourses) {
		return totalCourses + ":fbquantiles:cache";
	}

	/**
	 * totalCourses별 전체 인원수. globalRank.headcount의 분모.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "T(com.mjusugangsincheonghelper.singlegame.service.SingleGameStatsService).headCountKey(#totalCourses)", sync = true)
	public long getHeadCount(int totalCourses) {
		return singleGameRepository.countDistinctPersons(totalCourses);
	}

	/**
	 * totalCourses+dept별 학과 인원수. 회원 조회일 때만 호출된다.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "T(com.mjusugangsincheonghelper.singlegame.service.SingleGameStatsService).deptHeadCountKey(#totalCourses, #department)", sync = true)
	public long getDeptHeadCount(int totalCourses, String department) {
		return singleGameRepository.countDistinctDeptPersons(totalCourses, department);
	}

	/**
	 * totalCourses별 전체 구간 분위수표(7점). SequenceQuantileTable 원천.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "T(com.mjusugangsincheonghelper.singlegame.service.SingleGameStatsService).quantilesKey(#totalCourses)", sync = true)
	public SequenceQuantileTable getGlobalSegmentQuantiles(int totalCourses) {
		return SequenceQuantileTable.builder()
				.segmentQuantiles(loadSegmentQuantiles(singleGameRepository.findGlobalSegmentQuantiles(totalCourses)))
				.enterMain(loadEnterQuantiles(singleGameRepository.findGlobalEnterQuantiles(totalCourses)))
				.build();
	}

	/**
	 * totalCourses+dept별 학과 구간 분위수표(7점). 회원 조회일 때만 호출된다.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "T(com.mjusugangsincheonghelper.singlegame.service.SingleGameStatsService).deptQuantilesKey(#totalCourses, #department)", sync = true)
	public SequenceQuantileTable getDepartmentSegmentQuantiles(int totalCourses, String department) {
		return SequenceQuantileTable.builder()
				.segmentQuantiles(loadSegmentQuantiles(
						singleGameRepository.findDepartmentSegmentQuantiles(totalCourses, department)))
				.enterMain(loadEnterQuantiles(
						singleGameRepository.findDepartmentEnterQuantiles(totalCourses, department)))
				.build();
	}

	/**
	 * totalCourses별 피드백 분위수표(7점). 회원 조회일 때만 호출된다.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "T(com.mjusugangsincheonghelper.singlegame.service.SingleGameStatsService).feedbackQuantilesKey(#totalCourses)", sync = true)
	public FeedbackQuantileTable getFeedbackQuantiles(int totalCourses) {
		List<Object[]> rows = singleGameRepository.findFeedbackQuantiles(totalCourses);
		if (rows.isEmpty()) {
			return FeedbackQuantileTable.empty();
		}
		Object[] row = rows.get(0);
		return FeedbackQuantileTable.builder()
				.sampleSize(toLong(row[0]))
				.aimAvg(quantileBand(row, 1))
				.burstAvg(quantileBand(row, 8))
				.firstTotal(quantileBand(row, 15))
				.paceStd(quantileBand(row, 22))
				.build();
	}

	/**
	 * 통계 캐시 무효화. dev 진단 cold 측정용. 키 포맷은 위 static 키 메서드가 단일 원천이다.
	 */
	public void evictStatsCaches(int totalCourses, String department) {
		Cache cache = cacheManager.getCache(CacheProperties.SINGLEGAME_STATS);
		if (cache == null) {
			return;
		}
		cache.evict(headCountKey(totalCourses));
		cache.evict(quantilesKey(totalCourses));
		cache.evict(feedbackQuantilesKey(totalCourses));
		if (department != null && !department.isBlank()) {
			cache.evict(deptHeadCountKey(totalCourses, department));
			cache.evict(deptQuantilesKey(totalCourses, department));
		}
	}

	// ============ 내부 매핑: DB double → QuantileBand(int, ms) ============

	private Map<Integer, EnumMap<SegmentMetric, QuantileBand>> loadSegmentQuantiles(List<Object[]> rows) {
		Map<Integer, EnumMap<SegmentMetric, QuantileBand>> table = new HashMap<>();
		for (Object[] row : rows) {
			int sequence = toInt(row[0]);
			EnumMap<SegmentMetric, QuantileBand> perMetric = new EnumMap<>(SegmentMetric.class);
			perMetric.put(SegmentMetric.AIM, quantileBand(row, 1));
			perMetric.put(SegmentMetric.CONFIRM, quantileBand(row, 8));
			perMetric.put(SegmentMetric.COMPLETE, quantileBand(row, 15));
			perMetric.put(SegmentMetric.TOTAL, quantileBand(row, 22));
			table.put(sequence, perMetric);
		}
		return table;
	}

	private QuantileBand loadEnterQuantiles(List<Object[]> rows) {
		if (rows.isEmpty()) {
			return null;
		}
		return quantileBand(rows.get(0), 0);
	}

	private QuantileBand quantileBand(Object[] row, int offset) {
		int[] q = new int[7];
		for (int i = 0; i < 7 && offset + i < row.length; i++) {
			Object o = row[offset + i];
			q[i] = (o instanceof Number n) ? (int) Math.round(n.doubleValue()) : 0;
		}
		return QuantileBand.builder()
				.q05(q[0]).q10(q[1]).q30(q[2]).q50(q[3]).q70(q[4]).q90(q[5]).q95(q[6])
				.build();
	}

	private long toLong(Object o) {
		if (o instanceof Number n) {
			return n.longValue();
		}
		return 0L;
	}

	private int toInt(Object o) {
		if (o instanceof Number n) {
			return n.intValue();
		}
		return 0;
	}
}
