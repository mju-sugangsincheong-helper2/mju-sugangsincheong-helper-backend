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
 * 싱글게임 공용 랭킹 스냅샷 캐시 (singlegame-rank).
 *
 * <p>다른 도메인(Exchange/MultiGame/Latency/System)과 동일하게 선언형
 * {@code @Cacheable} read-through 캐시를 사용한다. {@code myRank}는 요청자마다
 * 다르므로 캐시하지 않고, 공용 랭킹 목록만 캐시한다. 개인 순위는 호출자
 * ({@link SingleGameService})가 요청마다 DB에서 직접 계산한다.</p>
 *
 * <p>키 공간은 totalCourses 종류(5개) × (GLOBAL 1 + 학과 수)로 유계이므로
 * 명시적 evict 없이 TTL(5m)만 사용한다 — singlegame-stats/multigame-rank와
 * 동일한 TTL 기반 패턴이다.</p>
 *
 * <p>scope/department는 호출자가 정규화한 값을 전달하는 것이 전제다
 * ({@link #normalizeScope}, {@link #normalizeDepartment}). GLOBAL의 department
 * 자리는 고정값 {@code ALL}을 써서 키가 안정되도록 한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SingleGameRankingCacheService {

	private static final int RANKING_LIMIT = 20;

	private final SingleGameRepository singleGameRepository;

	/** scope 정규화: DEPARTMENT(대소문자 무시) 외에는 모두 GLOBAL. */
	public static String normalizeScope(String scope) {
		return "DEPARTMENT".equalsIgnoreCase(scope) ? "DEPARTMENT" : "GLOBAL";
	}

	/**
	 * department 정규화: GLOBAL이면 키 안정용 고정값 ALL,
	 * DEPARTMENT면 호출자가 해석한 학과명(빈 값 불가).
	 */
	public static String normalizeDepartment(String normalizedScope, String resolvedDepartment) {
		if ("DEPARTMENT".equals(normalizedScope)) {
			return resolvedDepartment;
		}
		return "ALL";
	}

	/**
	 * 공용 랭킹 스냅샷. myRank는 포함하지 않는다.
	 *
	 * @param scope 정규화된 scope (GLOBAL/DEPARTMENT)
	 * @param department 정규화된 department (GLOBAL이면 ALL, DEPARTMENT면 학과명)
	 */
	@Cacheable(value = CacheProperties.SINGLEGAME_RANK, key = "#totalCourses + ':' + #scope + ':' + #department + ':cache'", sync = true)
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
			int rank = i + 1;
			for (int j = i - 1; j >= 0; j--) {
				if (toInt(raw.get(j)[5]) == tTotal) {
					rank = j + 1;
				} else {
					break;
				}
			}
			rankings.add(RankingEntry.builder()
					.rank(rank)
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
		if (name == null || name.isEmpty()) return name;
		int len = name.length();
		if (len == 1) return "*";
		return "*".repeat(len - 1) + name.charAt(len - 1);
	}

	private long toLong(Object o) {
		if (o instanceof Number n) return n.longValue();
		return 0L;
	}

	private int toInt(Object o) {
		if (o instanceof Number n) return n.intValue();
		return 0;
	}
}
