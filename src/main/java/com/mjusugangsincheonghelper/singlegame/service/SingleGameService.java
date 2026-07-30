package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.singlegame.dto.DepartmentsResponse;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.AnalysisDetail;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.AnalysisSummary;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.DataBucket;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional(readOnly = true)
public class SingleGameService {

	private final SingleGameRepository singleGameRepository;
	private final SingleGameDetailRepository singleGameDetailRepository;
	private final MemberRepository memberRepository;
	private final CacheManager cacheManager;
	private final SingleGameFeedbackEngine feedbackEngine;
	private final int reactionTimeMinMs;
	private final int reactionTimeMaxMs;

	public SingleGameService(
			SingleGameRepository singleGameRepository,
			SingleGameDetailRepository singleGameDetailRepository,
			MemberRepository memberRepository,
			CacheManager cacheManager,
			SingleGameFeedbackEngine feedbackEngine,
			@org.springframework.beans.factory.annotation.Value("${app.singlegame.reaction-time-min-ms:1}") int reactionTimeMinMs,
			@org.springframework.beans.factory.annotation.Value("${app.singlegame.reaction-time-max-ms:60000}") int reactionTimeMaxMs) {
		this.singleGameRepository = singleGameRepository;
		this.singleGameDetailRepository = singleGameDetailRepository;
		this.memberRepository = memberRepository;
		this.cacheManager = cacheManager;
		this.feedbackEngine = feedbackEngine;
		this.reactionTimeMinMs = reactionTimeMinMs;
		this.reactionTimeMaxMs = reactionTimeMaxMs;
	}

	private static final List<Integer> ALLOWED_TOTAL_COURSES = List.of(1, 3, 6, 7, 8);

	public DepartmentsResponse getDepartments() {
		List<String> departments = singleGameRepository.findDistinctDepartments();
		return DepartmentsResponse.builder()
				.departments(departments)
				.build();
	}

	@Transactional
	public SingleGameSaveResponse saveGame(Long memberId, SingleGameSaveRequest request) {
		if (!memberRepository.existsById(memberId)) {
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

		if (request.getTEnterMain() < reactionTimeMinMs || request.getTEnterMain() > reactionTimeMaxMs) {
			throw new BaseException(ErrorCode.SINGLEGAME_INVALID_REACTION_TIME);
		}

		for (SingleGameDetailRequest d : request.getDetails()) {
			if (d.getTClickCourse() < reactionTimeMinMs || d.getTClickCourse() > reactionTimeMaxMs) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_REACTION_TIME);
			}
			if (d.getTClickYes() < reactionTimeMinMs || d.getTClickYes() > reactionTimeMaxMs) {
				throw new BaseException(ErrorCode.SINGLEGAME_INVALID_REACTION_TIME);
			}
			if (d.getTClickOk() < reactionTimeMinMs || d.getTClickOk() > reactionTimeMaxMs) {
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

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				evictRankingCaches(totalCourses);
				evictMemberRecordCaches(memberId);
			}
		});

		return SingleGameSaveResponse.builder()
				.gameId(gameId)
				.message("게임 결과가 성공적으로 기록되었습니다.")
				.build();
	}

	@Cacheable(value = "singlegame-rank", key = "#totalCourses + ':' + #scope + ':' + #department + ':cache'", sync = true)
	public RankingResponse getRankings(int totalCourses, String scope, String department, Long memberId) {
		List<Object[]> raw;
		if ("DEPARTMENT".equalsIgnoreCase(scope)) {
			String dept = department;
			if (dept == null || dept.isBlank()) {
				Member me = memberRepository.findById(memberId)
						.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
				dept = me.getDepartment();
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
					.name((String) row[2])
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

	@Cacheable(value = "singlegame-analysis", key = "#gameId + ':cache'", sync = true)
	public AnalysisResponse getAnalysis(long gameId) {
		SingleGameEntity game = singleGameRepository.findById(gameId)
				.orElseThrow(() -> new BaseException(ErrorCode.SINGLEGAME_GAME_NOT_FOUND));

		List<SingleGameDetailEntity> details = singleGameDetailRepository
				.findByGameIdOrderBySequenceAsc(gameId);

		int N = details.size();
		List<Integer> clickCourses = details.stream().map(SingleGameDetailEntity::getTClickCourse).toList();
		List<Integer> clickYesses = details.stream().map(SingleGameDetailEntity::getTClickYes).toList();
		List<Integer> clickOks = details.stream().map(SingleGameDetailEntity::getTClickOk).toList();
		List<Integer> totals = details.stream().map(d -> d.getTClickCourse() + d.getTClickYes() + d.getTClickOk()).toList();
		int totalCourses = game.getTotalCourses();

		double myAvgClickCourse = clickCourses.stream().mapToInt(Integer::intValue).average().orElse(0);
		double myAvgBurst = 0;
		for (int i = 0; i < N; i++) {
			myAvgBurst += clickYesses.get(i) + clickOks.get(i);
		}
		myAvgBurst = N > 0 ? myAvgBurst / N : 0;
		int myT1Total = N > 0 ? totals.get(0) : 0;

		double myAvgTotal = totals.stream().mapToInt(Integer::intValue).average().orElse(0);
		Double initialSprint = null;
		if (N >= 3) {
			double laterAvg = totals.subList(1, N).stream().mapToInt(Integer::intValue).average().orElse(0);
			initialSprint = totals.get(0) - laterAvg;
		}

		double myPaceStddev = 0;
		if (N >= 3) {
			double mean = myAvgTotal;
			double variance = totals.stream().mapToDouble(t -> Math.pow(t - mean, 2)).sum() / N;
			myPaceStddev = Math.sqrt(variance);
		}

		Map<Integer, double[]> percentileStats = new HashMap<>();
		List<Object[]> viewRows = singleGameRepository.findSequencePercentileStats(totalCourses);
		for (Object[] row : viewRows) {
			int seq = toInt(row[1]);
			double[] stats = new double[20];
			for (int i = 0; i < 20 && i + 2 < row.length; i++) {
				stats[i] = toDouble(row[i + 2]);
			}
			percentileStats.put(seq, stats);
		}

		List<AnalysisDetail> analysisDetails = new ArrayList<>();
		for (int i = 0; i < N; i++) {
			int seq = details.get(i).getSequence();
			double[] stats = percentileStats.getOrDefault(seq, new double[20]);

			analysisDetails.add(AnalysisDetail.builder()
					.sequence(seq)
					.mine(DataBucket.builder()
							.clickCourse(clickCourses.get(i))
							.clickYes(clickYesses.get(i))
							.clickOk(clickOks.get(i))
							.total(totals.get(i))
							.build())
					.p10(DataBucket.builder()
							.clickCourse((int) stats[0]).clickYes((int) stats[5]).clickOk((int) stats[10]).total((int) stats[15])
							.build())
					.p30(DataBucket.builder()
							.clickCourse((int) stats[1]).clickYes((int) stats[6]).clickOk((int) stats[11]).total((int) stats[16])
							.build())
					.p50(DataBucket.builder()
							.clickCourse((int) stats[2]).clickYes((int) stats[7]).clickOk((int) stats[12]).total((int) stats[17])
							.build())
					.p70(DataBucket.builder()
							.clickCourse((int) stats[3]).clickYes((int) stats[8]).clickOk((int) stats[13]).total((int) stats[18])
							.build())
					.p100(DataBucket.builder()
							.clickCourse((int) stats[4]).clickYes((int) stats[9]).clickOk((int) stats[14]).total((int) stats[19])
							.build())
					.build());
		}

		int globalRank = computeRank(totalCourses, game.getTTotal());
		long totalPlayers = singleGameRepository.countByTotalCoursesAndIsCompletedTrue(totalCourses);
		double globalPercentile = totalPlayers > 0 ? (double) (globalRank - 1) / totalPlayers * 100 : 0;

		List<Object[]> allDetails = singleGameRepository.findAllDetailsByTotalCourses(totalCourses);
		Map<Long, List<Object[]>> detailsByGame = new HashMap<>();
		for (Object[] row : allDetails) {
			detailsByGame.computeIfAbsent(toLong(row[0]), k -> new ArrayList<>()).add(row);
		}

		List<Double> allAvgClickCourse = new ArrayList<>();
		List<Double> allAvgBurst = new ArrayList<>();
		List<Integer> allT1Total = new ArrayList<>();
		List<Double> allPaceStddev = new ArrayList<>();

		for (List<Object[]> gameDetails : detailsByGame.values()) {
			gameDetails.sort(Comparator.comparingInt(r -> toInt(r[1])));
			int gN = gameDetails.size();
			double sumCC = 0, sumBurst = 0;
			List<Integer> gTotals = new ArrayList<>();
			for (Object[] d : gameDetails) {
				int cc = toInt(d[2]), cy = toInt(d[3]), cok = toInt(d[4]);
				sumCC += cc;
				sumBurst += cy + cok;
				gTotals.add(cc + cy + cok);
			}
			allAvgClickCourse.add(sumCC / gN);
			allAvgBurst.add(sumBurst / gN);
			allT1Total.add(gTotals.isEmpty() ? 0 : gTotals.get(0));

			if (gN >= 3) {
				double mean = gTotals.stream().mapToInt(Integer::intValue).average().orElse(0);
				double variance = gTotals.stream().mapToDouble(t -> Math.pow(t - mean, 2)).sum() / gN;
				allPaceStddev.add(Math.sqrt(variance));
			}
		}

		double aimP = computePercentileDouble(allAvgClickCourse, myAvgClickCourse);
		double burstP = computePercentileDouble(allAvgBurst, myAvgBurst);
		double eP = computeEnterMainPercentile(totalCourses, game.getTEnterMain());
		double startP = computePercentileInt(allT1Total, myT1Total);
		double paceP = N >= 3 ? computePercentileDouble(allPaceStddev, myPaceStddev) : 0;

		SingleGameFeedbackEngine.FeedbackResult feedback = feedbackEngine.determineFeedback(aimP, burstP, eP, startP, paceP, N,
				totals, myAvgTotal, myPaceStddev);

		AnalysisSummary summary = AnalysisSummary.builder()
				.totalTime(game.getTTotal())
				.globalRank(globalRank)
				.globalPercentile(Math.round(globalPercentile * 10.0) / 10.0)
				.purePhysicalAverage((int) Math.round(myAvgTotal))
				.entryPrecision(game.getTEnterMain())
				.initialSprintSpeed(initialSprint != null ? initialSprint.intValue() : null)
				.paceDeviation(N >= 3 ? Math.round(myPaceStddev * 100.0) / 100.0 : null)
				.feedbackCode(feedback.code())
				.feedbackMessage(feedback.message())
				.build();

		return AnalysisResponse.builder()
				.gameId(gameId)
				.totalCourses(totalCourses)
				.completed(game.isCompleted())
				.summary(summary)
				.details(analysisDetails)
				.build();
	}

	private MyRecordResponse buildMyRecordResponse(SingleGameEntity g, String myDept) {
		int globalRank = computeRank(g.getTotalCourses(), g.getTTotal());
		long totalPlayers = singleGameRepository.countByTotalCoursesAndIsCompletedTrue(g.getTotalCourses());

		double globalPercentile = totalPlayers > 0 ? (double) (globalRank - 1) / totalPlayers * 100 : 0;

		int deptRank = 0;
		int deptPlayers = 0;
		double deptPercentile = 0;
		if (myDept != null) {
			List<Long> deptRanked = singleGameRepository
					.findDeptRankedGameIds(g.getTotalCourses(), myDept);
			deptPlayers = deptRanked.size();
			for (int i = 0; i < deptRanked.size(); i++) {
				if (deptRanked.get(i).equals(g.getId())) {
					deptRank = i + 1;
					break;
				}
			}
			deptPercentile = deptPlayers > 0 ? (double) (deptRank - 1) / deptPlayers * 100 : 0;
		}

		return MyRecordResponse.builder()
				.gameId(g.getId())
				.totalCourses(g.getTotalCourses())
				.completed(g.isCompleted())
				.tTotal(g.getTTotal())
				.tEnterMain(g.getTEnterMain())
				.createdAt(g.getCreatedAt())
				.ranking(RecordRanking.builder()
						.global(RankInfo.builder()
								.rank(globalRank)
								.totalParticipants((int) totalPlayers)
								.percentile(Math.round(globalPercentile * 10.0) / 10.0)
								.build())
						.department(RankInfo.builder()
								.rank(deptRank)
								.totalParticipants(deptPlayers)
								.percentile(Math.round(deptPercentile * 10.0) / 10.0)
								.build())
						.build())
				.build();
	}

	private RecordCacheDto toRecordCacheDto(SingleGameEntity g, String myDept) {
		MyRecordResponse response = buildMyRecordResponse(g, myDept);
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
						.department(RecordCacheDto.RankInfo.builder()
								.rank(response.getRanking().getDepartment().getRank())
								.totalParticipants(response.getRanking().getDepartment().getTotalParticipants())
								.percentile(response.getRanking().getDepartment().getPercentile())
								.build())
						.build())
				.build();
	}

	private MyRecordResponse toMyRecordResponse(RecordCacheDto dto) {
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
						.department(RankInfo.builder()
								.rank(dto.getRanking().getDepartment().getRank())
								.totalParticipants(dto.getRanking().getDepartment().getTotalParticipants())
								.percentile(dto.getRanking().getDepartment().getPercentile())
								.build())
						.build())
				.build();
	}

	private void evictRankingCaches(int totalCourses) {
		Cache cache = cacheManager.getCache("singlegame-rank");
		if (cache != null) {
			cache.clear();
		}
	}

	private void evictMemberRecordCaches(Long memberId) {
		Cache cache = cacheManager.getCache("singlegame-records");
		if (cache != null) {
			cache.evict(memberId + ":page:0:size:10:cache");
		}
	}



	private double computePercentileDouble(List<Double> allValues, double myValue) {
		if (allValues.isEmpty()) return 0;
		long lessOrEqual = allValues.stream().filter(v -> v <= myValue).count();
		return Math.max(0, (double) (lessOrEqual - 1) / allValues.size() * 100);
	}

	private double computePercentileInt(List<Integer> allValues, int myValue) {
		if (allValues.isEmpty()) return 0;
		long lessOrEqual = allValues.stream().filter(v -> v <= myValue).count();
		return Math.max(0, (double) (lessOrEqual - 1) / allValues.size() * 100);
	}

	private double computeEnterMainPercentile(int totalCourses, int myEnterMain) {
		List<Long> betterOrEqual = singleGameRepository
				.findGameIdsWithBetterOrEqualEnterMain(totalCourses, myEnterMain);
		long total = singleGameRepository.countByTotalCoursesAndIsCompletedTrue(totalCourses);
		if (total == 0) return 0;
		return Math.max(0, (double) (betterOrEqual.size() - 1) / total * 100);
	}

	private int computeRank(int totalCourses, int tTotal) {
		List<Long> betterOrEqual = singleGameRepository
				.findGameIdsWithBetterOrEqualTTotal(totalCourses, tTotal);
		return betterOrEqual.size();
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
