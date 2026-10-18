package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import com.mjusugangsincheonghelper.singlegame.dto.cache.FeedbackBands;
import com.mjusugangsincheonghelper.singlegame.dto.cache.PopulationTable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 싱글게임 통계 계산 전담 서비스.
 *
 * <p>캐시하는 값은 전부 "최종 답" 크기다: 인원수 1숫자, 구간표(수십 개 int),
 * 피드백 밴드(16개 int + n). 게임별 원시 리스트를 캐시하지 않으므로
 * 캐시 미스 시에도 전수 로우 전송·JVM 집계·거대 JSON 직렬화가 발생하지 않는다.</p>
 *
 * <p>키 공간은 totalCourses 종류(5개) × (1 + 학과 수)로 유계이므로
 * TTL이 유일한 신선도 수단이다. 미스는 사용자 경로에서 동기 재계산되며
 * {@code sync=true}로 키당 single-flight를 보장한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SingleGameStatsService {

	private final SingleGameRepository singleGameRepository;

	/**
	 * totalCourses별 전체 인원수(S1). globalRank.totalPersons의 분모.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "#totalCourses + ':counts:cache'", sync = true)
	public long getHeadCount(int totalCourses) {
		return singleGameRepository.countDistinctPersons(totalCourses);
	}

	/**
	 * totalCourses+dept별 학과 인원수(S2). 회원 조회일 때만 호출된다.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "#totalCourses + ':dept:' + #department + ':counts:cache'", sync = true)
	public long getDeptHeadCount(int totalCourses, String department) {
		return singleGameRepository.countDistinctDeptPersons(totalCourses, department);
	}

	/**
	 * totalCourses별 전체 구간 분포표(S3): 구간×지표 p-table + 진입 p-table.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "#totalCourses + ':pop:cache'", sync = true)
	public PopulationTable getPopulation(int totalCourses) {
		return PopulationTable.builder()
				.seqBands(loadSeqBands(singleGameRepository.findGlobalSequenceBands(totalCourses)))
				.enterMain(loadEnterMain(singleGameRepository.findEnterMainPercentileStats(totalCourses)))
				.build();
	}

	/**
	 * totalCourses+dept별 학과 구간 분포표(S4). 회원 조회일 때만 호출된다.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "#totalCourses + ':dept:' + #department + ':pop:cache'", sync = true)
	public PopulationTable getDeptPopulation(int totalCourses, String department) {
		return PopulationTable.builder()
				.seqBands(loadSeqBands(singleGameRepository.findDeptSequenceBands(totalCourses, department)))
				.enterMain(loadEnterMain(singleGameRepository.findDeptEnterMainPercentileStats(totalCourses, department)))
				.build();
	}

	/**
	 * totalCourses별 피드백 분포표(S5). 1행 집계이므로 결과는 상수 크기다.
	 * 회원 조회일 때만 호출된다.
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_STATS, key = "#totalCourses + ':fb:cache'", sync = true)
	public FeedbackBands getFeedbackBands(int totalCourses) {
		List<Object[]> rows = singleGameRepository.findFeedbackBands(totalCourses);
		if (rows.isEmpty()) {
			return FeedbackBands.empty();
		}
		Object[] row = rows.get(0);
		return FeedbackBands.builder()
				.n(toLong(row[0]))
				.aim(band(row, 1))
				.burst(band(row, 5))
				.first(band(row, 9))
				.pace(band(row, 13))
				.build();
	}

	// ============ 내부 매핑 (DB double → int, 응답이 int이므로 손실 없음) ============

	private Map<Integer, int[]> loadSeqBands(List<Object[]> rows) {
		Map<Integer, int[]> stats = new HashMap<>();
		for (Object[] row : rows) {
			int seq = toInt(row[0]);
			int[] s = new int[16];
			for (int i = 0; i < 16 && i + 1 < row.length; i++) {
				s[i] = rounded(row[i + 1]);
			}
			stats.put(seq, s);
		}
		return stats;
	}

	private int[] loadEnterMain(List<Object[]> rows) {
		if (rows.isEmpty()) {
			return null;
		}
		return band(rows.get(0), 0);
	}

	private int[] band(Object[] row, int offset) {
		int[] b = new int[4];
		for (int i = 0; i < 4 && offset + i < row.length; i++) {
			b[i] = rounded(row[offset + i]);
		}
		return b;
	}

	private int rounded(Object o) {
		if (o instanceof Number n) {
			return (int) Math.round(n.doubleValue());
		}
		return 0;
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
