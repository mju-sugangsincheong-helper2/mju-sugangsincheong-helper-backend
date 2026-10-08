package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import com.mjusugangsincheonghelper.singlegame.config.SingleGameProperties;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.DeptRankInfo;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.DetailEvent;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.PopulationStats;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.RankInfo;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.TimelineEvent;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.RecordInfo;
import com.mjusugangsincheonghelper.singlegame.dto.DepartmentsResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse.MyRankInfo;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse.RankingEntry;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import com.mjusugangsincheonghelper.singlegame.dto.cache.StatsBundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class SingleGameService {

	private static final int RANKING_LIMIT = 20;

	private final SingleGameRepository singleGameRepository;
	private final SingleGameDetailRepository singleGameDetailRepository;
	private final MemberRepository memberRepository;
	private final SingleGameFeedbackEngine feedbackEngine;
	private final SingleGameProperties properties;
	private final SingleGameDataMergeService singleGameDataMergeService;
	private final SingleGameStatsService singleGameStatsService;
	private final CacheManager cacheManager;

	public SingleGameService(
			SingleGameRepository singleGameRepository,
			SingleGameDetailRepository singleGameDetailRepository,
			MemberRepository memberRepository,
			SingleGameFeedbackEngine feedbackEngine,
			SingleGameProperties properties,
			SingleGameDataMergeService singleGameDataMergeService,
			SingleGameStatsService singleGameStatsService,
			CacheManager cacheManager) {
		this.singleGameRepository = singleGameRepository;
		this.singleGameDetailRepository = singleGameDetailRepository;
		this.memberRepository = memberRepository;
		this.feedbackEngine = feedbackEngine;
		this.properties = properties;
		this.singleGameDataMergeService = singleGameDataMergeService;
		this.singleGameStatsService = singleGameStatsService;
		this.cacheManager = cacheManager;
	}

	private static final List<Integer> ALLOWED_TOTAL_COURSES = List.of(1, 3, 6, 7, 8);

	public DepartmentsResponse getDepartments() {
		List<String> departments = singleGameRepository.findDistinctDepartments();
		return DepartmentsResponse.builder()
				.departments(departments)
				.build();
	}

	@Transactional
	public SingleGameSaveResponse saveGame(Long memberId, SingleGameSaveRequest request) {		if (!memberRepository.existsById(memberId)) {
			throw new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND);
		}

		if (!ALLOWED_TOTAL_COURSES.contains(request.getTotalCourses())) {
			throw new BaseException(ErrorCode.SINGLEGAME_INVALID_TOTAL_COURSES);
		}

		int detailsCount = request.getDetails().size();
		int totalCourses = request.getTotalCourses();
		if (request.isCompleted()) {
			if (detailsCount != totalCourses) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_DETAILS_COUNT);
			}
		} else {
			if (detailsCount >= totalCourses) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_DETAILS_COUNT);
			}
		}

		SingleGameProperties.Timing timing = properties.getTiming();
		if (outOfRange(request.getTEnterMain(), timing.getTEnterMain())) {
			throw new BaseException(ErrorCode.SINGLEGAME_INVALID_REACTION_TIME);
		}

		for (SingleGameDetailRequest d : request.getDetails()) {
			if (outOfRange(d.getTClickCourse(), timing.getTClickCourse())) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_REACTION_TIME);
			}
			if (outOfRange(d.getTClickYes(), timing.getTClickYes())) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_REACTION_TIME);
			}
			if (outOfRange(d.getTClickOk(), timing.getTClickOk())) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_REACTION_TIME);
			}
		}

		int tTotal = request.getTEnterMain();
		for (SingleGameDetailRequest d : request.getDetails()) {
			tTotal += d.getTClickCourse() + d.getTClickYes() + d.getTClickOk();
		}

		SingleGameEntity game = SingleGameEntity.builder()
				.memberId(memberId)
				.tTotal(tTotal)
				.tEnterMain(request.getTEnterMain())
				.isCompleted(request.isCompleted())
				.totalCourses(request.getTotalCourses())
				.build();
		game = singleGameRepository.save(game);

		Long gameId = game.getId();
		List<SingleGameDetailEntity> details = request.getDetails().stream()
				.map(d -> SingleGameDetailEntity.builder()
						.gameId(gameId)
						.sequence(d.getSequence())
						.tClickCourse(d.getTClickCourse())
						.tClickYes(d.getTClickYes())
						.tClickOk(d.getTClickOk())
						.build())
				.toList();
		singleGameDetailRepository.saveAll(details);

		// 통계 캐시는 건드리지 않는다. TTL 만료로만 갱신해 쿼리 시간을 일정하게 유지한다.

		log.debug("Saved single game record. memberId={}, gameId={}, totalCourses={}, completed={}, tTotal={}",
				memberId, gameId, totalCourses, request.isCompleted(), tTotal);

		return SingleGameSaveResponse.builder()
				.gameId(gameId)
				.message("게임 결과가 성공적으로 기록되었습니다.")
				.build();
	}

	/**
	 * 랭킹 조회.
	 *
	 * <p>P1 fix: 공유 캐시에는 공용 랭킹 목록만 저장하고, myRank는 요청마다 별도 계산한다.
	 * scope/department 정규화는 캐시 키 계산 전에 수행한다.</p>
	 */
	public RankingResponse getRankings(int totalCourses, String scope, String department, Long memberId) {
		// 1. scope/department 정규화 (캐시 키 정확성)
		Member member = (memberId != null) ? memberRepository.findById(memberId).orElse(null) : null;
		String myDept = member != null ? member.getDepartment() : null;

		String resolvedScope = scope;
		String resolvedDept = department;
		if ("DEPARTMENT".equalsIgnoreCase(scope)) {
			if (myDept == null || myDept.isBlank()) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_RANK_DEPARTMENT);
			}
			if (resolvedDept == null || resolvedDept.isBlank()) {
				resolvedDept = myDept;
			}
		}

		// 2. 공용 랭킹 스냅샷 (캐시 공유, myRank 없음)
		RankingResponse base = getRankingsSnapshot(totalCourses, resolvedScope, resolvedDept);

		// 3. 개인 myRank (요청마다 계산, 캐시 비타기, scope 기준)
		MyRankInfo myRank = computeMyRank(totalCourses, resolvedScope, resolvedDept, memberId);

		// 4. 조합
		return RankingResponse.builder()
				.totalCourses(base.getTotalCourses())
				.scope(base.getScope())
				.department(base.getDepartment())
				.rankings(base.getRankings())
				.myRank(myRank)
				.build();
	}

	/**
	 * 공용 랭킹 스냅샷을 캐시에서 가져오거나 계산한다.
	 * CacheManager 직접 사용으로 self-invocation 문제 없이 cache-aside 패턴 적용.
	 */
	private RankingResponse getRankingsSnapshot(int totalCourses, String scope, String department) {
		Cache cache = cacheManager.getCache(CacheProperties.SINGLEGAME_RANK);
		String cacheKey = totalCourses + ":" + scope + ":" + department + ":cache";
		if (cache != null) {
			RankingResponse cached = cache.get(cacheKey, RankingResponse.class);
			if (cached != null) {
				return cached;
			}
		}
		RankingResponse computed = computeRankingsSnapshot(totalCourses, scope, department);
		if (cache != null) {
			cache.put(cacheKey, computed);
		}
		return computed;
	}

	/**
	 * DB에서 랭킹 스냅샷을 계산한다. myRank는 포함하지 않는다.
	 * raw는 인당 대표 1판 ORDER BY t_total이므로, 동점자는 앞선 사람 수 + 1(같은 블록 첫 번호)을 준다 (00 §7).
	 */
	private RankingResponse computeRankingsSnapshot(int totalCourses, String scope, String department) {
		List<Object[]> raw;
		if ("DEPARTMENT".equalsIgnoreCase(scope)) {
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
				.department("DEPARTMENT".equalsIgnoreCase(scope) ? department : null)
				.rankings(rankings)
				.build();
	}

	/**
	 * 요청자의 대표판(인당 최고 기록) 랭킹을 계산한다. scope이 DEPARTMENT면 해당 학과 내 순위.
	 * 캐시와 무관하게 DB에서 직접 계산. 완료판이 없으면 null.
	 */
	private MyRankInfo computeMyRank(int totalCourses, String scope, String department, Long memberId) {
		if (memberId == null) {
			return null;
		}
		List<Object[]> bestRows = singleGameRepository.findMyBestGame(memberId, totalCourses);
		if (bestRows == null || bestRows.isEmpty()) {
			return null;
		}
		Object[] best = bestRows.get(0);
		int bestTTotal = toInt(best[1]);
		int myRank;
		if ("DEPARTMENT".equalsIgnoreCase(scope) && department != null && !department.isBlank()) {
			myRank = (int) singleGameRepository.countBetterDeptPersons(totalCourses, department, bestTTotal) + 1;
		} else {
			myRank = (int) singleGameRepository.countBetterPersons(totalCourses, bestTTotal) + 1;
		}
		return MyRankInfo.builder()
				.rank(myRank)
				.gameId(toLong(best[0]))
				.tTotal(bestTTotal)
				.tEnterMain(toInt(best[2]))
				.build();
	}

	public Page<MyRecordResponse> getMyRecords(Long memberId, int page, int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<SingleGameEntity> gamesPage = singleGameRepository
				.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
		List<SingleGameEntity> games = gamesPage.getContent();

		List<MyRecordResponse> records = games.stream().map(this::buildMyRecordResponse).toList();

		return new PageImpl<>(records, pageable, gamesPage.getTotalElements());
	}

	/**
	 * 게임 분석 응답을 조합한다.
	 *
	 * <p>원본(game, details)은 DB에서, 통계는 {@link SingleGameStatsService} 캐시에서 가져온다.
	 * 응답 자체는 캐시하지 않으므로 stale 문제가 구조적으로 차단된다.</p>
	 */
	public AnalysisResponse getAnalysis(long gameId, Long memberId) {
		SingleGameEntity game = singleGameRepository.findById(gameId)
				.orElseThrow(() -> new BaseException(ErrorCode.SINGLEGAME_GAME_NOT_FOUND));

		List<SingleGameDetailEntity> details = singleGameDetailRepository
				.findByGameIdOrderBySequenceAsc(gameId);

		int totalCourses = game.getTotalCourses();

		boolean isOwner = memberId != null && game.getMemberId().equals(memberId);
		Member gameOwner = memberRepository.findById(game.getMemberId()).orElse(null);
		String ownerDept = gameOwner != null ? gameOwner.getDepartment() : null;
		if (ownerDept != null && ownerDept.isBlank()) {
			ownerDept = null;
		}

		Member viewer = memberId != null ? memberRepository.findById(memberId).orElse(null) : null;
		boolean fullAccess = viewer != null && viewer.getRole() != Member.Role.GUEST;
		boolean isMember = gameOwner != null && gameOwner.getRole() != Member.Role.GUEST;

		// 통계는 캐시된 StatsBundle에서
		StatsBundle globalStats = singleGameStatsService.getGlobalStats(totalCourses);

		StatsBundle deptStats = null;
		if (ownerDept != null) {
			deptStats = singleGameStatsService.getDeptStats(totalCourses, ownerDept);
		}

		RankInfo globalRank = buildGlobalRank(totalCourses, game.getTTotal());
		DeptRankInfo deptRank = null;
		if (fullAccess && ownerDept != null) {
			deptRank = buildDeptRank(totalCourses, ownerDept, game.getTTotal());
		}

		List<DetailEvent> detail = buildDetailEvents(game, details,
				globalStats.getSeqPercentileStats(),
				globalStats.getEnterMainPercentileStats(),
				deptStats != null ? deptStats.getSeqPercentileStats() : null,
				deptStats != null ? deptStats.getEnterMainPercentileStats() : null,
				fullAccess);

		var feedbacks = fullAccess
				? buildFeedbacks(game, details, globalStats.getAggregates())
				: null;

		return AnalysisResponse.builder()
				.gameId(gameId)
				.isOwner(isOwner)
				.isMember(isMember)
				.totalCourses(totalCourses)
				.record(RecordInfo.builder()
						.tTotal(game.getTTotal())
						.tEnterMain(game.getTEnterMain())
						.completed(game.isCompleted())
						.createdAt(game.getCreatedAt())
						.build())
				.globalRank(globalRank)
				.departmentRank(deptRank)
				.globalTimeline(buildGlobalTimeline(detail))
				.departmentTimeline(fullAccess && deptStats != null ? buildDeptTimeline(detail) : null)
				.detail(detail)
				.feedbacks(feedbacks)
				.build();
	}

	/**
	 * 이 판의 전체 순위. 사람 기준(00 §5): 나보다 최고 기록이 좋은 사람 수 + 1 (00 §7).
	 */
	private RankInfo buildGlobalRank(int totalCourses, int tTotal) {
		int rank = (int) singleGameRepository.countBetterPersons(totalCourses, tTotal) + 1;
		long totalPersons = singleGameRepository.countDistinctPersons(totalCourses);
		double percentile = totalPersons > 0 ? (double) (rank - 1) / totalPersons * 100 : 0;
		return RankInfo.builder()
				.rank(rank)
				.totalPersons(totalPersons)
				.percentile(Math.round(percentile * 10.0) / 10.0)
				.build();
	}

	/**
	 * 이 판의 학과 순위. 판 주인 학과 기준(00 §6), 동점 정책은 전체와 동일(00 §7).
	 */
	private DeptRankInfo buildDeptRank(int totalCourses, String ownerDept, int tTotal) {
		int rank = (int) singleGameRepository.countBetterDeptPersons(totalCourses, ownerDept, tTotal) + 1;
		long totalPersons = singleGameRepository.countDistinctDeptPersons(totalCourses, ownerDept);
		double percentile = totalPersons > 0 ? (double) (rank - 1) / totalPersons * 100 : 0;
		return DeptRankInfo.builder()
				.department(ownerDept)
				.rank(rank)
				.totalPersons(totalPersons)
				.percentile(Math.round(percentile * 10.0) / 10.0)
				.build();
	}

	/**
	 * 차트용 타임라인. 표(detail)에서 duration과 분포만 뽑는다. 등급·백분위는 제외.
	 */
	private List<TimelineEvent> buildGlobalTimeline(List<DetailEvent> detail) {
		return detail.stream()
				.map(d -> TimelineEvent.builder()
						.sequence(d.getSequence())
						.type(d.getType())
						.label(d.getLabel())
						.durationMs(d.getDurationMs())
						.population(d.getGlobalPopulation())
						.build())
				.toList();
	}

	private List<TimelineEvent> buildDeptTimeline(List<DetailEvent> detail) {
		return detail.stream()
				.map(d -> TimelineEvent.builder()
						.sequence(d.getSequence())
						.type(d.getType())
						.label(d.getLabel())
						.durationMs(d.getDurationMs())
						.population(d.getDepartmentPopulation())
						.build())
				.toList();
	}

	private List<DetailEvent> buildDetailEvents(SingleGameEntity game, List<SingleGameDetailEntity> details,
			Map<Integer, double[]> globalSeqStats, double[] globalEntryStats,
			Map<Integer, double[]> deptSeqStats, double[] deptEntryStats, boolean fullAccess) {
		List<DetailEvent> events = new ArrayList<>();

		double entryP = computeEnterMainPercentile(game.getTotalCourses(), game.getTEnterMain());

		PopulationStats entryGlobalPop = null;
		if (globalEntryStats != null) {
			entryGlobalPop = PopulationStats.builder()
					.p10((int) globalEntryStats[0])
					.p30((int) globalEntryStats[1])
					.p50((int) globalEntryStats[2])
					.p70((int) globalEntryStats[3])
					.build();
		}

		PopulationStats entryDeptPop = null;
		if (deptEntryStats != null) {
			entryDeptPop = PopulationStats.builder()
					.p10((int) deptEntryStats[0])
					.p30((int) deptEntryStats[1])
					.p50((int) deptEntryStats[2])
					.p70((int) deptEntryStats[3])
					.build();
		}

		events.add(DetailEvent.builder()
				.sequence(0)
				.type("ENTRY")
				.label("메인방 진입")
				.durationMs(game.getTEnterMain())
				.percentile(Math.round(entryP * 10.0) / 10.0)
				.grade(fullAccess ? computeGrade(entryP) : null)
				.globalPopulation(entryGlobalPop)
				.departmentPopulation(fullAccess ? entryDeptPop : null)
				.build());

		for (SingleGameDetailEntity d : details) {
			int seq = d.getSequence();
			double[] gStats = globalSeqStats.getOrDefault(seq, new double[20]);
			double[] dStats = deptSeqStats != null ? deptSeqStats.getOrDefault(seq, new double[16]) : null;

			events.add(buildDetailEvent(seq, "AIM", seq + "순위 과목 조준", d.getTClickCourse(),
					gStats, 0, 1, 2, 3, dStats, 0, 1, 2, 3, fullAccess));
			events.add(buildDetailEvent(seq, "CONFIRM", "신청 확인", d.getTClickYes(),
					gStats, 4, 5, 6, 7, dStats, 4, 5, 6, 7, fullAccess));
			events.add(buildDetailEvent(seq, "COMPLETE", "완료 확인", d.getTClickOk(),
					gStats, 8, 9, 10, 11, dStats, 8, 9, 10, 11, fullAccess));
		}

		return events;
	}

	private DetailEvent buildDetailEvent(int seq, String type, String label, int durationMs,
			double[] gStats, int gP10, int gP30, int gP50, int gP70,
			double[] dStats, int dP10, int dP30, int dP50, int dP70, boolean fullAccess) {
		int gp10 = (int) gStats[gP10];
		int gp30 = (int) gStats[gP30];
		int gp50 = (int) gStats[gP50];
		int gp70 = (int) gStats[gP70];
		double percentile = interpolatePercentile(durationMs, gp10, gp30, gp50, gp70);

		PopulationStats globalPop = PopulationStats.builder()
				.p10(gp10).p30(gp30).p50(gp50).p70(gp70)
				.build();

		PopulationStats deptPop = null;
		if (dStats != null) {
			deptPop = PopulationStats.builder()
					.p10((int) dStats[dP10])
					.p30((int) dStats[dP30])
					.p50((int) dStats[dP50])
					.p70((int) dStats[dP70])
					.build();
		}

		return DetailEvent.builder()
				.sequence(seq)
				.type(type)
				.label(label)
				.durationMs(durationMs)
				.percentile(Math.round(percentile * 10.0) / 10.0)
				.grade(fullAccess ? computeGrade(percentile) : null)
				.globalPopulation(globalPop)
				.departmentPopulation(fullAccess ? deptPop : null)
				.build();
	}

	private double computeEnterMainPercentile(int totalCourses, int tEnterMain) {
		long betterOrEqual = singleGameRepository
				.countEnterMainBetterOrEqual(totalCourses, tEnterMain);
		long total = singleGameRepository.countCompletedGames(totalCourses);
		if (total == 0) return 0;
		return Math.max(0, (double) (betterOrEqual - 1) / total * 100);
	}

	private double interpolatePercentile(int value, int p10, int p30, int p50, int p70) {
		if (p10 <= 0) return 0;
		if (value <= p10) return Math.max(0, (double) value / p10 * 10);
		if (value <= p30) return 10 + (double) (value - p10) / (p30 - p10) * 20;
		if (value <= p50) return 30 + (double) (value - p30) / (p50 - p30) * 20;
		if (value <= p70) return 50 + (double) (value - p50) / (p70 - p50) * 20;
		return 70 + Math.min(30, (double) (value - p70) / p70 * 30);
	}

	private AnalysisResponse.FeedbacksResponse buildFeedbacks(SingleGameEntity game,
			List<SingleGameDetailEntity> details, List<double[]> allAggregates) {
		int N = details.size();
		double myAvgCC = details.stream().mapToInt(SingleGameDetailEntity::getTClickCourse).average().orElse(0);
		double myAvgCY = details.stream().mapToInt(SingleGameDetailEntity::getTClickYes).average().orElse(0);
		double myAvgCOK = details.stream().mapToInt(SingleGameDetailEntity::getTClickOk).average().orElse(0);
		double myAvgBurst = myAvgCY + myAvgCOK;
		List<Integer> totals = details.stream()
				.map(d -> d.getTClickCourse() + d.getTClickYes() + d.getTClickOk()).toList();
		double myAvgTotal = totals.stream().mapToInt(Integer::intValue).average().orElse(0);

		double aimP = computePercentileFromAggregates(allAggregates, myAvgCC, 0, false);
		double burstP = computePercentileFromAggregates(allAggregates, myAvgBurst, 3, false);
		double eP = computeEnterMainPercentile(game.getTotalCourses(), game.getTEnterMain());
		double startP = computePercentileFromAggregates(allAggregates,
				N > 0 ? (double) totals.get(0) : 0, 4, false);
		double paceP = 0;
		double paceStddev = 0;
		if (N >= 3) {
			double mean = myAvgTotal;
			double variance = totals.stream().mapToDouble(t -> Math.pow(t - mean, 2)).sum() / N;
			paceStddev = Math.sqrt(variance);
			List<Double> allPaceDevs = allAggregates.stream()
					.filter(a -> a[5] > 0).map(a -> a[5]).toList();
			paceP = computePercentileDouble(allPaceDevs, paceStddev);
		}

		return feedbackEngine.determineFeedbacks(aimP, burstP, eP, startP, paceP, N, totals, myAvgTotal, paceStddev);
	}

	private MyRecordResponse buildMyRecordResponse(SingleGameEntity g) {
		return MyRecordResponse.builder()
				.gameId(g.getId())
				.totalCourses(g.getTotalCourses())
				.completed(g.isCompleted())
				.tTotal(g.getTTotal())
				.createdAt(g.getCreatedAt())
				.build();
	}



	private double computePercentileFromAggregates(List<double[]> aggregates, double myValue, int idx,
			boolean isEnterMain) {
		if (aggregates.isEmpty()) return 0;
		if (isEnterMain) {
			long betterOrEqual = aggregates.stream().filter(a -> a[idx] <= myValue).count();
			return Math.max(0, (double) (betterOrEqual - 1) / aggregates.size() * 100);
		}
		long lessOrEqual = aggregates.stream().filter(a -> a[idx] <= myValue).count();
		return Math.max(0, (double) (lessOrEqual - 1) / aggregates.size() * 100);
	}

	private double computePercentileDouble(List<Double> allValues, double myValue) {
		if (allValues.isEmpty()) return 0;
		long lessOrEqual = allValues.stream().filter(v -> v <= myValue).count();
		return Math.max(0, (double) (lessOrEqual - 1) / allValues.size() * 100);
	}

	private String computeGrade(double percentile) {
		if (percentile <= 5) return "S";
		if (percentile <= 30) return "A";
		if (percentile < 70) return "B";
		if (percentile < 95) return "C";
		return "D";
	}

	private boolean outOfRange(int value, SingleGameProperties.EventTiming timing) {
		return value < timing.getMinMs() || value > timing.getMaxMs();
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
