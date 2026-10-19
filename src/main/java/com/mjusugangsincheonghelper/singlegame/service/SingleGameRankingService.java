package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.singlegame.dto.DepartmentsResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse.MyRankInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 싱글게임 랭킹 전담. 공용 랭킹 스냅샷은 캐시하고 myRank는 요청마다 계산한다.
 *
 * <p>키 공간은 totalCourses 종류(5개) × (GLOBAL 1 + 학과 수)로 유계이므로
 * TTL 만료로만 갱신한다. myRank는 요청자마다 다르므로 캐시하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SingleGameRankingService {

	private final SingleGameRepository singleGameRepository;
	private final MemberRepository memberRepository;
	private final SingleGameRankingSnapshotCache snapshotCache;

	public RankingResponse getRankings(int totalCourses, String scope, String department, Long viewerId) {
		Member viewer = (viewerId != null) ? memberRepository.findById(viewerId).orElse(null) : null;
		String viewerDepartment = viewer != null ? viewer.getDepartment() : null;

		String resolvedScope = normalizeScope(scope);
		String resolvedDepartment;
		if ("DEPARTMENT".equals(resolvedScope)) {
			if (viewerDepartment == null || viewerDepartment.isBlank()) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_RANK_DEPARTMENT);
			}
			resolvedDepartment = (department == null || department.isBlank()) ? viewerDepartment
					: department.strip();
		} else {
			resolvedDepartment = normalizeDepartment(resolvedScope, null);
		}

		RankingResponse snapshot = snapshotCache.getRankingsSnapshot(totalCourses, resolvedScope, resolvedDepartment);

		MyRankInfo myRank = computeMyRank(totalCourses, resolvedScope, resolvedDepartment, viewerId);

		return RankingResponse.builder()
				.totalCourses(snapshot.getTotalCourses())
				.scope(snapshot.getScope())
				.department(snapshot.getDepartment())
				.rankings(snapshot.getRankings())
				.myRank(myRank)
				.build();
	}

	public DepartmentsResponse getDepartments() {
		List<String> departments = singleGameRepository.findDistinctDepartments();
		return DepartmentsResponse.builder()
				.departments(departments)
				.build();
	}

	private MyRankInfo computeMyRank(int totalCourses, String scope, String department, Long viewerId) {
		if (viewerId == null) {
			return null;
		}
		List<Object[]> bestRows = singleGameRepository.findMyBestGame(viewerId, totalCourses);
		if (bestRows == null || bestRows.isEmpty()) {
			return null;
		}
		Object[] best = bestRows.get(0);
		int bestTTotal = toInt(best[1]);
		int personRank;
		if ("DEPARTMENT".equalsIgnoreCase(scope) && department != null && !department.isBlank()) {
			personRank = (int) singleGameRepository.countBetterDeptPersons(totalCourses, department, bestTTotal)
					+ 1;
		} else {
			personRank = (int) singleGameRepository.countBetterPersons(totalCourses, bestTTotal) + 1;
		}
		return MyRankInfo.builder()
				.rank(personRank)
				.gameId(toLong(best[0]))
				.tTotal(bestTTotal)
				.tEnterMain(toInt(best[2]))
				.build();
	}

	public static String normalizeScope(String scope) {
		return "DEPARTMENT".equalsIgnoreCase(scope) ? "DEPARTMENT" : "GLOBAL";
	}

	public static String normalizeDepartment(String normalizedScope, String resolvedDepartment) {
		if ("DEPARTMENT".equals(normalizedScope)) {
			return resolvedDepartment;
		}
		return "ALL";
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
