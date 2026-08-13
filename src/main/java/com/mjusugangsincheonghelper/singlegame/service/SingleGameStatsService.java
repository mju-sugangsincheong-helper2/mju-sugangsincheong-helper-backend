package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import com.mjusugangsincheonghelper.singlegame.dto.cache.StatsBundle;
import java.util.ArrayList;
import java.util.Comparator;
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
 * <p>통계 데이터는 totalCourses(및 선택적 dept) 단위로 캐시된다.
 * 응답 조합은 {@link SingleGameService}에서 수행한다.</p>
 *
 * <p>렌즈 관점: L2(스냅샷 허용) + L4(키 공간 유계).
 * evict 불필요, TTL이 유일한 신선도 수단.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SingleGameStatsService {

	private final SingleGameRepository singleGameRepository;
	private final CacheManager cacheManager;

	/**
	 * totalCourses별 전역 통계 번들을 가져온다. 캐시 미스 시 DB에서 계산.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "#totalCourses + ':global:cache'", sync = true)
	public StatsBundle getGlobalStats(int totalCourses) {
		return StatsBundle.builder()
				.seqPercentileStats(loadSeqPercentileStats(totalCourses))
				.enterMainPercentileStats(loadEnterMainPercentileStats(totalCourses))
				.aggregates(loadAllAggregates(totalCourses))
				.build();
	}

	/**
	 * totalCourses+dept별 학과 통계 번들을 가져온다. 캐시 미스 시 DB에서 계산.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "#totalCourses + ':dept:' + #department + ':cache'", sync = true)
	public StatsBundle getDeptStats(int totalCourses, String department) {
		return StatsBundle.builder()
				.seqPercentileStats(loadDeptSeqPercentileStats(totalCourses, department))
				.enterMainPercentileStats(loadDeptEnterMainPercentileStats(totalCourses, department))
				.build();
	}

	/**
	 * 해당 totalCourses의 모든 통계 캐시를 무효화한다.
	 * 새 게임 저장 시 호출.
	 */
	public void evict(int totalCourses) {
		Cache cache = cacheManager.getCache(CacheProperties.SINGLEGAME_STATS);
		if (cache == null) return;

		// global 캐시 evict
		cache.evict(totalCourses + ":global:cache");

		// dept 캐시는 조회된 학과만 evict (전체 학과 목록 조회는 비용이 크므로 생략)
		// TTL이 자동으로 정리하므로 여기서는 global만 명시적 evict
		log.debug("Evicted single game stats cache. totalCourses={}", totalCourses);
	}

	// ============ 내부 로딩 메서드 ============

	private Map<Integer, double[]> loadSeqPercentileStats(int totalCourses) {
		Map<Integer, double[]> stats = new HashMap<>();
		List<Object[]> viewRows = singleGameRepository.findSequencePercentileStats(totalCourses);
		for (Object[] row : viewRows) {
			int seq = toInt(row[1]);
			double[] s = new double[16];
			for (int i = 0; i < 16 && i + 2 < row.length; i++) {
				s[i] = toDouble(row[i + 2]);
			}
			stats.put(seq, s);
		}
		return stats;
	}

	private double[] loadEnterMainPercentileStats(int totalCourses) {
		List<Object[]> rows = singleGameRepository.findEnterMainPercentileStats(totalCourses);
		if (rows.isEmpty()) return null;
		Object[] row = rows.get(0);
		return new double[]{toDouble(row[0]), toDouble(row[1]), toDouble(row[2]), toDouble(row[3])};
	}

	private Map<Integer, double[]> loadDeptSeqPercentileStats(int totalCourses, String department) {
		Map<Integer, double[]> stats = new HashMap<>();
		List<Object[]> viewRows = singleGameRepository.findDeptSequencePercentileStats(totalCourses, department);
		for (Object[] row : viewRows) {
			int seq = toInt(row[0]);
			double[] s = new double[16];
			for (int i = 0; i < 16 && i + 1 < row.length; i++) {
				s[i] = toDouble(row[i + 1]);
			}
			stats.put(seq, s);
		}
		return stats;
	}

	private double[] loadDeptEnterMainPercentileStats(int totalCourses, String department) {
		List<Object[]> rows = singleGameRepository.findDeptEnterMainPercentileStats(totalCourses, department);
		if (rows.isEmpty()) return null;
		Object[] row = rows.get(0);
		return new double[]{toDouble(row[0]), toDouble(row[1]), toDouble(row[2]), toDouble(row[3])};
	}

	private List<double[]> loadAllAggregates(int totalCourses) {
		List<Object[]> allDetails = singleGameRepository.findAllDetailsByTotalCourses(totalCourses);
		Map<Long, List<Object[]>> detailsByGame = new HashMap<>();
		for (Object[] row : allDetails) {
			detailsByGame.computeIfAbsent(toLong(row[0]), k -> new ArrayList<>()).add(row);
		}

		List<double[]> aggregates = new ArrayList<>();
		for (List<Object[]> gameDetails : detailsByGame.values()) {
			gameDetails.sort(Comparator.comparingInt(r -> toInt(r[1])));
			int gN = gameDetails.size();
			double sumCC = 0, sumCY = 0, sumCOK = 0;
			List<Integer> gTotals = new ArrayList<>();
			for (Object[] d : gameDetails) {
				int cc = toInt(d[2]), cy = toInt(d[3]), cok = toInt(d[4]);
				sumCC += cc;
				sumCY += cy;
				sumCOK += cok;
				gTotals.add(cc + cy + cok);
			}
			double avgCC = sumCC / gN;
			double avgCY = sumCY / gN;
			double avgCOK = sumCOK / gN;
			double avgBurst = (sumCY + sumCOK) / gN;
			int t1Total = gTotals.isEmpty() ? 0 : gTotals.get(0);
			double paceStddev = 0;
			double initialSprint = 0;
			double fatigueIndex = 0;
			if (gN >= 3) {
				double mean = gTotals.stream().mapToInt(Integer::intValue).average().orElse(0);
				double variance = gTotals.stream().mapToDouble(t -> Math.pow(t - mean, 2)).sum() / gN;
				paceStddev = Math.sqrt(variance);

				double laterAvg = gTotals.subList(1, gN).stream().mapToInt(Integer::intValue).average().orElse(0);
				initialSprint = gTotals.get(0) - laterAvg;

				int half = gN / 2;
				double firstHalfAvg = gTotals.subList(0, half).stream().mapToInt(Integer::intValue).average().orElse(0);
				double secondHalfAvg = gTotals.subList(gN - half, gN).stream().mapToInt(Integer::intValue).average().orElse(0);
				fatigueIndex = secondHalfAvg - firstHalfAvg;
			}
			aggregates.add(new double[]{avgCC, avgCY, avgCOK, avgBurst, t1Total, paceStddev, initialSprint, fatigueIndex});
		}
		return aggregates;
	}

	private long toLong(Object o) {
		if (o instanceof Number n) return n.longValue();
		return 0L;
	}

	private int toInt(Object o) {
		if (o instanceof Number n) return n.intValue();
		return 0;
	}

	private double toDouble(Object o) {
		if (o instanceof Number n) return n.doubleValue();
		return 0;
	}
}
