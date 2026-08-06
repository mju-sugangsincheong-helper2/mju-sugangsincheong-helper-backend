package com.mjusugangsincheonghelper.multigame.result.service;

import com.mjusugangsincheonghelper.database.repository.MultigameRoundMemberRepository;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멀티게임 랭킹 캐시 (multigame-rank).
 *
 * <p>학과 랭킹의 원본 집계(학과별 성공률 목록)만 read-through 캐시한다. 전체
 * {@code multigame_round_member} 테이블 GROUP BY(+Member JOIN)인
 * {@code aggregateByMemberDepartment()}가 멀티게임 도메인에서 유일하게 무거운 조회이므로
 * 이 결과만 캐시하고, 참여 수/상위 70% 성공률/myDepartment 응답은 요청마다 이 캐시 값에서
 * 순수 메모리 연산으로 파생한다 (학과 수는 수십 개 수준이라 파생 비용은 무시 가능).</p>
 *
 * <p>데이터 변경 주기는 게임 정산(주기적 일괄 배치)이므로 명시적 evict 없이 TTL(5m)만 사용한다
 * — exchange는 쓰기마다 afterCommit evict를 하지만, 랭킹 캐시(singlegame/multigame)는
 * TTL 기반 패턴을 유지한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MultigameCacheService {

	private static final String CACHE_NAME_RANK = CacheProperties.MULTIGAME_RANK;

	/** 한 라운드에서 취득할 수 있는 최대 과목 수 (과목 1~6). */
	private static final int SUBJECT_COUNT_PER_ROUND = 6;

	private final MultigameRoundMemberRepository roundMemberRepository;

	/**
	 * 학과별 성공률 원본 집계. 랭킹 API에 파라미터가 없으므로 캐시 키는 도메인 전체 공유 단일 값이다.
	 */
	@Cacheable(cacheNames = CACHE_NAME_RANK, key = "'department:rates:cache'", sync = true)
	public Map<String, List<Double>> collectSuccessRatesByDepartment() {
		Map<String, List<Double>> ratesByDepartment = new HashMap<>();
		for (Object[] row : roundMemberRepository.aggregateByMemberDepartment()) {
			String department = (String) row[1];
			long successCount = ((Number) row[2]).longValue();
			long roundsPlayed = ((Number) row[3]).longValue();
			// 성공률 = 성공 과목 수 / (참여 라운드 수 × 6). 라운드당 최대 6과목 중 몇 개를
			// 쟁취했는지(평균 취득률)를 고정 분모로 측정한다. 신청하지 않은 라운드는 집계 대상이
			// 아니므로(멤버 레코드 없음), 진입만 한 유저는 성적 집계에서 제외된다.
			double successRate = roundsPlayed > 0 ? successCount * 100.0 / (roundsPlayed * SUBJECT_COUNT_PER_ROUND) : 0.0;
			ratesByDepartment.computeIfAbsent(department, key -> new ArrayList<>()).add(successRate);
		}
		return ratesByDepartment;
	}
}
