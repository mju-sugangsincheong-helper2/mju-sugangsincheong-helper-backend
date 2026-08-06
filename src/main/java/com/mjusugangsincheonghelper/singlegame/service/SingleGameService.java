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
import com.mjusugangsincheonghelper.singlegame.dto.cache.RecordCacheDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SingleGameService {

	private final SingleGameRepository singleGameRepository;
	private final SingleGameDetailRepository singleGameDetailRepository;
	private final MemberRepository memberRepository;
	private final SingleGameFeedbackEngine feedbackEngine;
	private final SingleGameProperties properties;

	public SingleGameService(
			SingleGameRepository singleGameRepository,
			SingleGameDetailRepository singleGameDetailRepository,
			MemberRepository memberRepository,
			SingleGameFeedbackEngine feedbackEngine,
			SingleGameProperties properties) {
		this.singleGameRepository = singleGameRepository;
		this.singleGameDetailRepository = singleGameDetailRepository;
		this.memberRepository = memberRepository;
		this.feedbackEngine = feedbackEngine;
		this.properties = properties;
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

		// singlegame 캐시(rank/records/analysis)는 TTL 기반이라 쓰기 시점에 evict 하지 않는다.

		return SingleGameSaveResponse.builder()
				.gameId(gameId)
				.message("게임 결과가 성공적으로 기록되었습니다.")
				.build();
	}

	@Cacheable(value = "singlegame-rank", key = "#totalCourses + ':' + #scope + ':' + #department + ':cache'", sync = true)
	public RankingResponse getRankings(int totalCourses, String scope, String department, Long memberId) {
		Member member = memberRepository.findById(memberId).orElse(null);
		String myDept = member != null ? member.getDepartment() : null;

		if ("DEPARTMENT".equalsIgnoreCase(scope) && (myDept == null || myDept.isBlank())) {
			scope = "GLOBAL";
		}

		List<Object[]> raw;
		if ("DEPARTMENT".equalsIgnoreCase(scope)) {
			String dept = department;
			if (dept == null || dept.isBlank()) {
				dept = myDept;
			}
			if (dept == null) dept = "";
			raw = singleGameRepository.findDeptRankingRaw(totalCourses, dept);
		} else {
			raw = singleGameRepository.findRankingRaw(totalCourses);
		}

		List<RankingEntry> allRankings = new ArrayList<>();
		int rank = 1;
		Map<Long, Integer> gameIdToRank = new HashMap<>();
		for (Object[] row : raw) {
			Long gameId = toLong(row[0]);
			allRankings.add(RankingEntry.builder()
					.rank(rank)
					.gameId(gameId)
					.name(maskName((String) row[2]))
					.department((String) row[3])
					.tTotal(toInt(row[5]))
					.tEnterMain(toInt(row[6]))
					.build());
			gameIdToRank.put(gameId, rank);
			rank++;
		}

		List<RankingEntry> rankings = allRankings.stream().limit(20).toList();

		MyRankInfo myRank = null;
		if (memberId != null) {
			Optional<SingleGameEntity> latestGame = singleGameRepository
					.findTopByMemberIdAndTotalCoursesAndIsCompletedTrueOrderByCreatedAtDesc(memberId, totalCourses);
			if (latestGame.isPresent()) {
				Long gameId = latestGame.get().getId();
				Integer myGameRank = gameIdToRank.get(gameId);
				if (myGameRank != null) {
					myRank = MyRankInfo.builder()
							.rank(myGameRank)
							.gameId(gameId)
							.tTotal(latestGame.get().getTTotal())
							.tEnterMain(latestGame.get().getTEnterMain())
							.build();
				}
			}
		}

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
				.myRank(myRank)
				.subRankings(subRankings)
				.build();
	}

	public Page<MyRecordResponse> getMyRecords(Long memberId, int page, int size) {
		if (page == 0 && size == 10) {
			List<RecordCacheDto> cached = getMyRecordsFirstPage(memberId);
			if (cached != null) {
				List<MyRecordResponse> records = cached.stream()
						.map(this::toMyRecordResponse)
						.toList();
				return new PageImpl<>(records, PageRequest.of(0, 10), records.size());
			}
		}

		Pageable pageable = PageRequest.of(page, size);
		Page<SingleGameEntity> gamesPage = singleGameRepository
				.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
		List<SingleGameEntity> games = gamesPage.getContent();

		Member member = memberRepository.findById(memberId).orElse(null);
		String myDept = member != null ? member.getDepartment() : null;

		List<MyRecordResponse> records = games.stream().map(g -> buildMyRecordResponse(g, myDept)).toList();

		return new PageImpl<>(records, pageable, gamesPage.getTotalElements());
	}

	@Cacheable(value = "singlegame-records", key = "#memberId + ':page:0:size:10:cache'", sync = true)
	public List<RecordCacheDto> getMyRecordsFirstPage(Long memberId) {
		Pageable pageable = PageRequest.of(0, 10);
		Page<SingleGameEntity> gamesPage = singleGameRepository
				.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
		List<SingleGameEntity> games = gamesPage.getContent();

		Member member = memberRepository.findById(memberId).orElse(null);
		String myDept = member != null ? member.getDepartment() : null;

		return games.stream()
				.map(g -> toRecordCacheDto(g, myDept))
				.toList();
	}

	@Cacheable(value = "singlegame-analysis", key = "#gameId + ':' + #memberId + ':cache'", sync = true)
	public AnalysisResponse getAnalysis(long gameId, Long memberId) {
		SingleGameEntity game = singleGameRepository.findById(gameId)
				.orElseThrow(() -> new BaseException(ErrorCode.SINGLEGAME_GAME_NOT_FOUND));

		List<SingleGameDetailEntity> details = singleGameDetailRepository
				.findByGameIdOrderBySequenceAsc(gameId);

		int totalCourses = game.getTotalCourses();

		boolean isOwner = game.getMemberId().equals(memberId);
		Member gameOwner = memberRepository.findById(game.getMemberId()).orElse(null);
		boolean isMember = gameOwner != null && gameOwner.getRole() != Member.Role.GUEST;

		RankingSummary ranking = buildRankingSummary(game, memberId);

		Map<Integer, double[]> globalSeqStats = loadSeqPercentileStats(totalCourses);
		double[] globalEntryStats = loadEnterMainPercentileStats(totalCourses);

		String myDept = null;
		Map<Integer, double[]> deptSeqStats = null;
		double[] deptEntryStats = null;
		if (isMember && gameOwner != null) {
			myDept = gameOwner.getDepartment();
			if (myDept != null && !myDept.isBlank()) {
				deptSeqStats = loadDeptSeqPercentileStats(totalCourses, myDept);
				deptEntryStats = loadDeptEnterMainPercentileStats(totalCourses, myDept);
			}
		}

		List<BasicEvent> basic = buildBasicEvents(game, details);
		List<DetailEvent> detail = buildDetailEvents(game, details, globalSeqStats, globalEntryStats, deptSeqStats, deptEntryStats);

		List<double[]> allAggregates = loadAllAggregates(totalCourses);
		var feedbacks = buildFeedbacks(game, details, allAggregates);

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

	private double[] loadEnterMainPercentileStats(int totalCourses) {
		List<Object[]> rows = singleGameRepository.findEnterMainPercentileStats(totalCourses);
		if (rows.isEmpty()) return null;
		Object[] row = rows.get(0);
		return new double[]{toDouble(row[0]), toDouble(row[1]), toDouble(row[2]), toDouble(row[3])};
	}

	private double[] loadDeptEnterMainPercentileStats(int totalCourses, String department) {
		List<Object[]> rows = singleGameRepository.findDeptEnterMainPercentileStats(totalCourses, department);
		if (rows.isEmpty()) return null;
		Object[] row = rows.get(0);
		return new double[]{toDouble(row[0]), toDouble(row[1]), toDouble(row[2]), toDouble(row[3])};
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

	private RecordCacheDto toRecordCacheDto(SingleGameEntity g, String myDept) {
		MyRecordResponse response = buildMyRecordResponse(g, myDept);
		RecordCacheDto.RankInfo cacheDept = null;
		if (response.getRanking().getDepartment() != null) {
			cacheDept = RecordCacheDto.RankInfo.builder()
					.rank(response.getRanking().getDepartment().getRank())
					.totalParticipants(response.getRanking().getDepartment().getTotalParticipants())
					.percentile(response.getRanking().getDepartment().getPercentile())
					.build();
		}
		return RecordCacheDto.builder()
				.gameId(response.getGameId())
				.totalCourses(response.getTotalCourses())
				.completed(response.isCompleted())
				.tTotal(response.getTTotal())
				.tEnterMain(response.getTEnterMain())
				.createdAt(response.getCreatedAt())
				.ranking(RecordCacheDto.RecordRanking.builder()
						.global(RecordCacheDto.RankInfo.builder()
								.rank(response.getRanking().getGlobal().getRank())
								.totalParticipants(response.getRanking().getGlobal().getTotalParticipants())
								.percentile(response.getRanking().getGlobal().getPercentile())
								.build())
						.department(cacheDept)
						.build())
				.build();
	}

	private MyRecordResponse toMyRecordResponse(RecordCacheDto dto) {
		MyRecordResponse.RankInfo deptInfo = null;
		if (dto.getRanking().getDepartment() != null) {
			deptInfo = RankInfo.builder()
					.rank(dto.getRanking().getDepartment().getRank())
					.totalParticipants(dto.getRanking().getDepartment().getTotalParticipants())
					.percentile(dto.getRanking().getDepartment().getPercentile())
					.build();
		}
		return MyRecordResponse.builder()
				.gameId(dto.getGameId())
				.totalCourses(dto.getTotalCourses())
				.completed(dto.isCompleted())
				.tTotal(dto.getTTotal())
				.tEnterMain(dto.getTEnterMain())
				.createdAt(dto.getCreatedAt())
				.ranking(RecordRanking.builder()
						.global(RankInfo.builder()
								.rank(dto.getRanking().getGlobal().getRank())
								.totalParticipants(dto.getRanking().getGlobal().getTotalParticipants())
								.percentile(dto.getRanking().getGlobal().getPercentile())
								.build())
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
