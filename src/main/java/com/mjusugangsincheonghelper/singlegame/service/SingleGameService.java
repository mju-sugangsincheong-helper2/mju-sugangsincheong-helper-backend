package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
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
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import com.mjusugangsincheonghelper.singlegame.dto.cache.FeedbackBands;
import com.mjusugangsincheonghelper.singlegame.dto.cache.PopulationTable;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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

	private final SingleGameRepository singleGameRepository;
	private final SingleGameDetailRepository singleGameDetailRepository;
	private final MemberRepository memberRepository;
	private final SingleGameFeedbackEngine feedbackEngine;
	private final SingleGameProperties properties;
	private final SingleGameDataMergeService singleGameDataMergeService;
	private final SingleGameStatsService singleGameStatsService;
	private final SingleGameRankingCacheService rankingCacheService;

	public SingleGameService(
			SingleGameRepository singleGameRepository,
			SingleGameDetailRepository singleGameDetailRepository,
			MemberRepository memberRepository,
			SingleGameFeedbackEngine feedbackEngine,
			SingleGameProperties properties,
			SingleGameDataMergeService singleGameDataMergeService,
			SingleGameStatsService singleGameStatsService,
			SingleGameRankingCacheService rankingCacheService) {
		this.singleGameRepository = singleGameRepository;
		this.singleGameDetailRepository = singleGameDetailRepository;
		this.memberRepository = memberRepository;
		this.feedbackEngine = feedbackEngine;
		this.properties = properties;
		this.singleGameDataMergeService = singleGameDataMergeService;
		this.singleGameStatsService = singleGameStatsService;
		this.rankingCacheService = rankingCacheService;
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
	 * <p>공유 캐시에는 공용 랭킹 목록만 저장하고, myRank는 요청마다 별도 계산한다.
	 * scope/department 정규화는 캐시 키 계산 전에 수행한다.</p>
	 */
	public RankingResponse getRankings(int totalCourses, String scope, String department, Long memberId) {
		// 1. scope/department 정규화 (캐시 키 정확성)
		Member member = (memberId != null) ? memberRepository.findById(memberId).orElse(null) : null;
		String myDept = member != null ? member.getDepartment() : null;

		String resolvedScope = SingleGameRankingCacheService.normalizeScope(scope);
		String resolvedDept;
		if ("DEPARTMENT".equals(resolvedScope)) {
			if (myDept == null || myDept.isBlank()) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_RANK_DEPARTMENT);
			}
			resolvedDept = (department == null || department.isBlank()) ? myDept : department.strip();
		} else {
			resolvedDept = SingleGameRankingCacheService.normalizeDepartment(resolvedScope, null);
		}

		// 2. 공용 랭킹 스냅샷 (캐시 공유, myRank 없음)
		RankingResponse base = rankingCacheService.getRankingsSnapshot(totalCourses, resolvedScope, resolvedDept);

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

		// 모집단 통계는 캐시에서. S3·S1은 항상, S4·S2·S5는 회원 조회일 때만.
		PopulationTable globalPop = singleGameStatsService.getPopulation(totalCourses);
		long totalPersons = singleGameStatsService.getHeadCount(totalCourses);
		double entryP = interpolateBand(game.getTEnterMain(), globalPop.getEnterMain());

		int better = (int) singleGameRepository.countBetterPersons(totalCourses, game.getTTotal()) + 1;
		RankInfo globalRank = buildGlobalRank(better, totalPersons);

		DeptRankInfo deptRank = null;
		PopulationTable deptPop = null;
		if (fullAccess && ownerDept != null) {
			deptPop = singleGameStatsService.getDeptPopulation(totalCourses, ownerDept);
			long deptTotal = singleGameStatsService.getDeptHeadCount(totalCourses, ownerDept);
			int deptBetter = (int) singleGameRepository.countBetterDeptPersons(totalCourses, ownerDept,
					game.getTTotal()) + 1;
			deptRank = buildDeptRank(ownerDept, deptBetter, deptTotal);
		}

		List<DetailEvent> detail = buildDetailEvents(game, details, globalPop, deptPop, entryP, fullAccess);

		var feedbacks = fullAccess
				? buildFeedbacks(game, details, globalPop.getEnterMain(),
						singleGameStatsService.getFeedbackBands(totalCourses), entryP)
				: null;

		return AnalysisResponse.builder()
				.gameId(gameId)
				.owner(isOwner)
				.member(isMember)
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
				.departmentTimeline(fullAccess && deptPop != null ? buildDeptTimeline(detail) : null)
				.detail(detail)
				.feedbacks(feedbacks)
				.build();
	}

	/**
	 * 이 판의 전체 순위. 사람 기준(00 §5): 나보다 최고 기록이 좋은 사람 수 + 1 (00 §7).
	 */
	private RankInfo buildGlobalRank(int rank, long totalPersons) {
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
	private DeptRankInfo buildDeptRank(String ownerDept, int rank, long totalPersons) {
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
			PopulationTable globalPop, PopulationTable deptPopOrNull, double entryP, boolean fullAccess) {
		List<DetailEvent> events = new ArrayList<>();

		PopulationStats entryGlobalPop = toPopulationOrNull(globalPop.getEnterMain());
		PopulationStats entryDeptPop = deptPopOrNull != null
				? toPopulationOrNull(deptPopOrNull.getEnterMain())
				: null;

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
			int[] gStats = seqBandsOrZero(globalPop, seq);
			int[] dStats = deptPopOrNull != null ? seqBandsOrZero(deptPopOrNull, seq) : null;

			events.add(buildDetailEvent(seq, "AIM", seq + "순위 과목 조준", d.getTClickCourse(),
					gStats, 0, 1, 2, 3, dStats, 0, 1, 2, 3, fullAccess));
			events.add(buildDetailEvent(seq, "CONFIRM", "신청 확인", d.getTClickYes(),
					gStats, 4, 5, 6, 7, dStats, 4, 5, 6, 7, fullAccess));
			events.add(buildDetailEvent(seq, "COMPLETE", "완료 확인", d.getTClickOk(),
					gStats, 8, 9, 10, 11, dStats, 8, 9, 10, 11, fullAccess));
		}

		return events;
	}

	private int[] seqBandsOrZero(PopulationTable table, int seq) {
		if (table == null || table.getSeqBands() == null) {
			return new int[16];
		}
		return table.getSeqBands().getOrDefault(seq, new int[16]);
	}

	private PopulationStats toPopulationOrNull(int[] band) {
		if (band == null || band.length < 4) {
			return null;
		}
		return PopulationStats.builder()
				.p10(band[0]).p30(band[1]).p50(band[2]).p70(band[3])
				.build();
	}

	private DetailEvent buildDetailEvent(int seq, String type, String label, int durationMs,
			int[] gStats, int gP10, int gP30, int gP50, int gP70,
			int[] dStats, int dP10, int dP30, int dP50, int dP70, boolean fullAccess) {
		int gp10 = gStats[gP10];
		int gp30 = gStats[gP30];
		int gp50 = gStats[gP50];
		int gp70 = gStats[gP70];
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

	private double interpolateBand(int value, int[] band) {
		if (band == null || band.length < 4) {
			return 0;
		}
		return interpolatePercentile(value, band[0], band[1], band[2], band[3]);
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
			List<SingleGameDetailEntity> details, int[] enterMainBand, FeedbackBands bands, double entryP) {
		if (bands == null) {
			bands = FeedbackBands.empty();
		}
		int N = details.size();
		double myAvgCC = details.stream().mapToInt(SingleGameDetailEntity::getTClickCourse).average().orElse(0);
		double myAvgCY = details.stream().mapToInt(SingleGameDetailEntity::getTClickYes).average().orElse(0);
		double myAvgCOK = details.stream().mapToInt(SingleGameDetailEntity::getTClickOk).average().orElse(0);
		double myAvgBurst = myAvgCY + myAvgCOK;
		List<Integer> totals = details.stream()
				.map(d -> d.getTClickCourse() + d.getTClickYes() + d.getTClickOk()).toList();
		double myAvgTotal = totals.stream().mapToInt(Integer::intValue).average().orElse(0);

		double aimP = interpolateBand((int) Math.round(myAvgCC), bands.getAim());
		double burstP = interpolateBand((int) Math.round(myAvgBurst), bands.getBurst());
		double eP = entryP;
		double startP = N > 0 ? interpolateBand(totals.get(0), bands.getFirst()) : 0;
		double paceP = 0;
		double paceStddev = 0;
		if (N >= 3) {
			double mean = myAvgTotal;
			double variance = totals.stream().mapToDouble(t -> Math.pow(t - mean, 2)).sum() / N;
			paceStddev = Math.sqrt(variance);
			paceP = interpolateBand((int) Math.round(paceStddev), bands.getPace());
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


	private long toLong(Object o) {
		if (o instanceof Number n) return n.longValue();
		return 0L;
	}

	private int toInt(Object o) {
		if (o instanceof Number n) return n.intValue();
		return 0;
	}

}
