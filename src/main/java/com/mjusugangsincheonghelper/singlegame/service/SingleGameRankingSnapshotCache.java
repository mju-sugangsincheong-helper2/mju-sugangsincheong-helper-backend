package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse.RankingEntry;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공용 랭킹 스냅샷 캐시. {@code myRank}는 요청자마다 다르므로 캐시하지 않는다.
 *
 * <p>별도 빈으로 분리한 이유: {@code @Cacheable}은 프록시 경유 호출에서만 동작하므로
 * 오케스트레이터({@link SingleGameRankingService})와 같은 빈에 두면
 * self-invocation으로 캐시가 우회된다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SingleGameRankingSnapshotCache {

	private static final int RANKING_LIMIT = 20;

	private final SingleGameRepository singleGameRepository;

	/** 키 포맷 단일 원천. */
	public static String snapshotKey(int totalCourses, String scope, String department) {
		return totalCourses + ":" + scope + ":" + department + ":cache";
	}

	/**
	 * 공용 랭킹 스냅샷. myRank는 포함하지 않는다.
	 *
	 * @param scope 정규화된 scope (GLOBAL/DEPARTMENT)
	 * @param department 정규화된 department (GLOBAL이면 ALL, DEPARTMENT면 학과명)
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_RANK, key = "T(com.mjusugangsincheonghelper.singlegame.service.SingleGameRankingSnapshotCache).snapshotKey(#totalCourses, #scope, #department)", sync = true)
	public RankingResponse getRankingsSnapshot(int totalCourses, String scope, String department) {
		List<Object[]> raw;
		if ("DEPARTMENT".equals(scope)) {
			String dept = (department != null && !department.isBlank()) ? department : "";
			raw = singleGameRepository.findDeptRankingRaw(totalCourses, dept, RANKING_LIMIT);
		} else {
			raw = singleGameRepository.findRankingRaw(totalCourses, RANKING_LIMIT);
		}

		List<RankingEntry> rankings = new ArrayList<>();
		for (int i = 0; i < raw.size(); i++) {
			Object[] row = raw.get(i);
			int tTotal = toInt(row[5]);
			int personRank = i + 1;
			for (int j = i - 1; j >= 0; j--) {
				if (toInt(raw.get(j)[5]) == tTotal) {
					personRank = j + 1;
				} else {
					break;
				}
			}
			rankings.add(RankingEntry.builder()
					.rank(personRank)
					.gameId(toLong(row[0]))
					.name(maskName((String) row[2]))
					.department((String) row[3])
					.tTotal(tTotal)
					.tEnterMain(toInt(row[6]))
					.build());
		}

		return RankingResponse.builder()
				.totalCourses(totalCourses)
				.scope(scope)
				.department("DEPARTMENT".equals(scope) ? department : null)
				.rankings(rankings)
				.build();
	}

	private String maskName(String name) {
		if (name == null || name.isEmpty()) {
			return name;
		}
		int len = name.length();
		if (len == 1) {
			return "*";
		}
		return "*".repeat(len - 1) + name.charAt(len - 1);
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
