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
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.BasicEvent;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.DetailEvent;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.PopulationStats;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.RankDetail;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.RankingSummary;
import com.mjusugangsincheonghelper.singlegame.dto.DepartmentsResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse.RankInfo;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse.RecordRanking;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse.MyRankInfo;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse.RankingEntry;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse.SubEntry;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse.SubRankings;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import com.mjusugangsincheonghelper.singlegame.dto.cache.StatsBundle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@Transactional(readOnly = true)
public class SingleGameService {

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

		// 완료된 게임이면 통계 캐시 evict (totalCourses 단위)
		if (request.isCompleted()) {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
					@Override
					public void afterCommit() {
						singleGameStatsService.evict(totalCourses);
					}
				});
			} else {
				singleGameStatsService.evict(totalCourses);
			}
		}

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
				resolvedScope = "GLOBAL";
				resolvedDept = null;
			} else if (resolvedDept == null || resolvedDept.isBlank()) {
				resolvedDept = myDept;
			}
		}

		// 2. 공용 랭킹 스냅샷 (캐시 공유, myRank 없음)
		RankingResponse base = getRankingsSnapshot(totalCourses, resolvedScope, resolvedDept);

		// 3. 개인 myRank (요청마다 계산, 캐시 비타기)
		MyRankInfo myRank = computeMyRank(totalCourses, memberId);

		// 4. 조합
		return RankingResponse.builder()
				.totalCourses(base.getTotalCourses())
				.scope(base.getScope())
				.rankings(base.getRankings())
				.myRank(myRank)
				.subRankings(base.getSubRankings())
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
	 */
	private RankingResponse computeRankingsSnapshot(int totalCourses, String scope, String department) {
		List<Object[]> raw;
		if ("DEPARTMENT".equalsIgnoreCase(scope)) {
			String dept = (department != null && !department.isBlank()) ? department : "";
			raw = singleGameRepository.findDeptRankingRaw(totalCourses, dept);
		} else {
			raw = singleGameRepository.findRankingRaw(totalCourses);
		}

		List<RankingEntry> allRankings = new ArrayList<>();
		int rank = 1;
		for (Object[] row : raw) {
			allRankings.add(RankingEntry.builder()
					.rank(rank)
					.gameId(toLong(row[0]))
					.name(maskName((String) row[2]))
					.department((String) row[3])
					.tTotal(toInt(row[5]))
					.tEnterMain(toInt(row[6]))
					.build());
			rank++;
		}

		List<RankingEntry> rankings = allRankings.stream().limit(20).toList();

		SubRankings subRankings = null;
		if (totalCourses >= 3) {
			Map<Long, Integer> firstClickByGame = new HashMap<>();
			List<Object[]> firstClicks = singleGameRepository.findFirstClickRaw(totalCourses);
			for (Object[] row : firstClicks) {
				firstClickByGame.put(toLong(row[0]), toInt(row[2]));
			}

			List<Object[]> allForSub;
			if ("GLOBAL".equalsIgnoreCase(scope)) {
				allForSub = raw;
			} else {
				allForSub = singleGameRepository.findRankingRaw(totalCourses);
			}
			Map<Long, Integer> enterMainByGame = new HashMap<>();
			for (Object[] row : allForSub) {
				enterMainByGame.put(toLong(row[0]), toInt(row[6]));
			}

			List<Object[]> enterMainSorted = allForSub.stream()
					.sorted(Comparator.comparingInt(r -> toInt(r[6])))
					.limit(3)
					.toList();
			List<SubEntry> enterMainTop3 = new ArrayList<>();
			for (int i = 0; i < enterMainSorted.size(); i++) {
				Object[] r = enterMainSorted.get(i);
				enterMainTop3.add(SubEntry.builder()
						.rank(i + 1)
						.name((String) r[2])
						.tEnterMain(toInt(r[6]))
						.tClickCourse1st(firstClickByGame.getOrDefault(toLong(r[0]), 0))
						.build());
			}

			List<SubEntry> firstClickTop3 = new ArrayList<>();
			List<Object[]> firstClickLimited = firstClicks.stream().limit(3).toList();
			for (int i = 0; i < firstClickLimited.size(); i++) {
				Object[] r = firstClickLimited.get(i);
				firstClickTop3.add(SubEntry.builder()
						.rank(i + 1)
						.name((String) r[1])
						.tClickCourse1st(toInt(r[2]))
						.tEnterMain(enterMainByGame.getOrDefault(toLong(r[0]), 0))
						.build());
			}
			subRankings = SubRankings.builder()
					.enterMainTop3(enterMainTop3)
					.firstClickTop3(firstClickTop3)
					.build();
		}

		return RankingResponse.builder()
				.totalCourses(totalCourses)
				.scope(scope)
				.rankings(rankings)
				.subRankings(subRankings)
				.build();
	}

	/**
	 * 요청자의 최신 게임 랭킹을 계산한다. 캐시와 무관하게 DB에서 직접 계산.
	 */
	private MyRankInfo computeMyRank(int totalCourses, Long memberId) {
		if (memberId == null) {
			return null;
		}
		Optional<SingleGameEntity> latestGame = singleGameRepository
				.findTopByMemberIdAndTotalCoursesAndIsCompletedTrueOrderByCreatedAtDesc(memberId, totalCourses);
		if (latestGame.isEmpty()) {
			return null;
		}
		SingleGameEntity game = latestGame.get();
		int myRank = computeRank(totalCourses, game.getTTotal());
		return MyRankInfo.builder()
				.rank(myRank)
				.gameId(game.getId())
				.tTotal(game.getTTotal())
				.tEnterMain(game.getTEnterMain())
				.build();
	}

	public Page<MyRecordResponse> getMyRecords(Long memberId, int page, int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<SingleGameEntity> gamesPage = singleGameRepository
				.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
		List<SingleGameEntity> games = gamesPage.getContent();

		Member member = memberRepository.findById(memberId).orElse(null);
		String myDept = member != null ? member.getDepartment() : null;

		List<MyRecordResponse> records = games.stream().map(g -> buildMyRecordResponse(g, myDept)).toList();

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

		boolean isOwner = game.getMemberId().equals(memberId);
		Member gameOwner = memberRepository.findById(game.getMemberId()).orElse(null);
		boolean isMember = gameOwner != null && gameOwner.getRole() != Member.Role.GUEST;

		// 통계는 캐시된 StatsBundle에서
		StatsBundle globalStats = singleGameStatsService.getGlobalStats(totalCourses);

		String myDept = null;
		StatsBundle deptStats = null;
		if (isMember && gameOwner != null) {
			myDept = gameOwner.getDepartment();
			if (myDept != null && !myDept.isBlank()) {
				deptStats = singleGameStatsService.getDeptStats(totalCourses, myDept);
			}
		}

		RankingSummary ranking = buildRankingSummary(game, memberId);

		List<BasicEvent> basic = buildBasicEvents(game, details);
		List<DetailEvent> detail = buildDetailEvents(game, details,
				globalStats.getSeqPercentileStats(),
				globalStats.getEnterMainPercentileStats(),
				deptStats != null ? deptStats.getSeqPercentileStats() : null,
				deptStats != null ? deptStats.getEnterMainPercentileStats() : null);

		var feedbacks = buildFeedbacks(game, details, globalStats.getAggregates());

		return AnalysisResponse.builder()
				.gameId(gameId)
				.isOwner(isOwner)
				.isMember(isMember)
				.totalCourses(totalCourses)
				.totalTime(game.getTTotal())
				.ranking(ranking)
				.basic(basic)
				.detail(detail)
				.feedbacks(feedbacks)
				.build();
	}

	private RankingSummary buildRankingSummary(SingleGameEntity game, Long memberId) {
		int totalCourses = game.getTotalCourses();
		int globalRank = computeRank(totalCourses, game.getTTotal());
		long totalPlayers = singleGameRepository.countByTotalCoursesAndIsCompletedTrue(totalCourses);
		double globalPercentile = totalPlayers > 0 ? (double) (globalRank - 1) / totalPlayers * 100 : 0;

		RankDetail globalDetail = RankDetail.builder()
				.rank(globalRank)
				.totalParticipants((int) totalPlayers)
				.percentile(Math.round(globalPercentile * 10.0) / 10.0)
				.build();

		RankDetail deptDetail = null;
		if (memberId != null) {
			Member member = memberRepository.findById(memberId).orElse(null);
			String myDept = member != null ? member.getDepartment() : null;
			if (myDept != null && !myDept.isBlank()) {
				List<Long> deptRanked = singleGameRepository
						.findDeptRankedGameIds(totalCourses, myDept);
				int deptPlayers = deptRanked.size();
				int deptRank = 0;
				for (int i = 0; i < deptRanked.size(); i++) {
					if (deptRanked.get(i).equals(game.getId())) {
						deptRank = i + 1;
						break;
					}
				}
				double deptPercentile = deptPlayers > 0 ? (double) (deptRank - 1) / deptPlayers * 100 : 0;
				deptDetail = RankDetail.builder()
						.rank(deptRank)
						.totalParticipants(deptPlayers)
						.percentile(Math.round(deptPercentile * 10.0) / 10.0)
						.build();
			}
		}

		return RankingSummary.builder()
				.global(globalDetail)
				.department(deptDetail)
				.build();
	}

	private List<BasicEvent> buildBasicEvents(SingleGameEntity game, List<SingleGameDetailEntity> details) {
		List<BasicEvent> events = new ArrayList<>();

		events.add(BasicEvent.builder()
				.sequence(0)
				.type("ENTRY")
				.label("메인방 진입")
				.durationMs(game.getTEnterMain())
				.build());

		for (SingleGameDetailEntity d : details) {
			int seq = d.getSequence();
			events.add(BasicEvent.builder()
					.sequence(seq)
					.type("AIM")
					.label(seq + "순위 과목 조준")
					.durationMs(d.getTClickCourse())
					.build());
			events.add(BasicEvent.builder()
					.sequence(seq)
					.type("CONFIRM")
					.label("신청 확인")
					.durationMs(d.getTClickYes())
					.build());
			events.add(BasicEvent.builder()
					.sequence(seq)
					.type("COMPLETE")
					.label("완료 확인")
					.durationMs(d.getTClickOk())
					.build());
		}

		return events;
	}

	private List<DetailEvent> buildDetailEvents(SingleGameEntity game, List<SingleGameDetailEntity> details,
			Map<Integer, double[]> globalSeqStats, double[] globalEntryStats,
			Map<Integer, double[]> deptSeqStats, double[] deptEntryStats) {
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
				.grade(computeGrade(entryP))
				.globalPopulation(entryGlobalPop)
				.departmentPopulation(entryDeptPop)
				.build());

		for (SingleGameDetailEntity d : details) {
			int seq = d.getSequence();
			double[] gStats = globalSeqStats.getOrDefault(seq, new double[20]);
			double[] dStats = deptSeqStats != null ? deptSeqStats.getOrDefault(seq, new double[16]) : null;

			events.add(buildDetailEvent(seq, "AIM", seq + "순위 과목 조준", d.getTClickCourse(),
					gStats, 0, 1, 2, 3, dStats, 0, 1, 2, 3));
			events.add(buildDetailEvent(seq, "CONFIRM", "신청 확인", d.getTClickYes(),
					gStats, 4, 5, 6, 7, dStats, 4, 5, 6, 7));
			events.add(buildDetailEvent(seq, "COMPLETE", "완료 확인", d.getTClickOk(),
					gStats, 8, 9, 10, 11, dStats, 8, 9, 10, 11));
		}

		return events;
	}

	private DetailEvent buildDetailEvent(int seq, String type, String label, int durationMs,
			double[] gStats, int gP10, int gP30, int gP50, int gP70,
			double[] dStats, int dP10, int dP30, int dP50, int dP70) {
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
				.grade(computeGrade(percentile))
				.globalPopulation(globalPop)
				.departmentPopulation(deptPop)
				.build();
	}

	private double computeEnterMainPercentile(int totalCourses, int tEnterMain) {
		List<Long> betterOrEqual = singleGameRepository
				.findGameIdsWithBetterOrEqualEnterMain(totalCourses, tEnterMain);
		long total = singleGameRepository.countByTotalCoursesAndIsCompletedTrue(totalCourses);
		if (total == 0) return 0;
		return Math.max(0, (double) (betterOrEqual.size() - 1) / total * 100);
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

	private MyRecordResponse buildMyRecordResponse(SingleGameEntity g, String myDept) {
		int globalRank = computeRank(g.getTotalCourses(), g.getTTotal());
		long totalPlayers = singleGameRepository.countByTotalCoursesAndIsCompletedTrue(g.getTotalCourses());
		double globalPercentile = totalPlayers > 0 ? (double) (globalRank - 1) / totalPlayers * 100 : 0;

		RankInfo globalInfo = RankInfo.builder()
				.rank(globalRank)
				.totalParticipants((int) totalPlayers)
				.percentile(Math.round(globalPercentile * 10.0) / 10.0)
				.build();

		RankInfo deptInfo = null;
		if (myDept != null && !myDept.isBlank()) {
			List<Long> deptRanked = singleGameRepository
					.findDeptRankedGameIds(g.getTotalCourses(), myDept);
			int deptPlayers = deptRanked.size();
			int deptRank = 0;
			for (int i = 0; i < deptRanked.size(); i++) {
				if (deptRanked.get(i).equals(g.getId())) {
					deptRank = i + 1;
					break;
				}
			}
			double deptPercentile = deptPlayers > 0 ? (double) (deptRank - 1) / deptPlayers * 100 : 0;
			deptInfo = RankInfo.builder()
					.rank(deptRank)
					.totalParticipants(deptPlayers)
					.percentile(Math.round(deptPercentile * 10.0) / 10.0)
					.build();
		}

		return MyRecordResponse.builder()
				.gameId(g.getId())
				.totalCourses(g.getTotalCourses())
				.completed(g.isCompleted())
				.tTotal(g.getTTotal())
				.tEnterMain(g.getTEnterMain())
				.createdAt(g.getCreatedAt())
				.ranking(RecordRanking.builder()
						.global(globalInfo)
						.department(deptInfo)
						.build())
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

	private int computeRank(int totalCourses, int tTotal) {
		List<Long> betterOrEqual = singleGameRepository
				.findGameIdsWithBetterOrEqualTTotal(totalCourses, tTotal);
		return betterOrEqual.size();
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

	private double toDouble(Object o) {
		if (o instanceof Number n) return n.doubleValue();
		return 0;
	}
}
