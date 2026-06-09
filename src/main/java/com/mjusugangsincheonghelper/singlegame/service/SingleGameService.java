package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SingleGameService {

	private final SingleGameRepository singleGameRepository;
	private final SingleGameDetailRepository singleGameDetailRepository;
	private final MemberRepository memberRepository;

	private static final List<Integer> ALLOWED_TOTAL_COURSES = List.of(1, 3, 6, 7, 8);

	@Transactional
	public SingleGameSaveResponse saveGame(Long memberId, SingleGameSaveRequest request) {
		if (!memberRepository.existsById(memberId)) {
			throw new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND);
		}

		if (!ALLOWED_TOTAL_COURSES.contains(request.getTotalCourses())) {
			throw new BaseException(ErrorCode.SINGLEGAME_INVALID_TOTAL_COURSES);
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

		return SingleGameSaveResponse.builder()
				.gameId(gameId)
				.message("게임 결과가 성공적으로 기록되었습니다.")
				.build();
	}

	public RankingResponse getRankings(int totalCourses, String scope, Long memberId) {
		List<Object[]> raw;
		if ("DEPARTMENT".equalsIgnoreCase(scope)) {
			Member me = memberRepository.findById(memberId)
					.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
			String dept = me.getDepartment();
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
		Pageable pageable = PageRequest.of(page, size);
		Page<SingleGameEntity> gamesPage = singleGameRepository
				.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
		List<SingleGameEntity> games = gamesPage.getContent();

		Member member = memberRepository.findById(memberId).orElse(null);
		String myDept = member != null ? member.getDepartment() : null;

		List<MyRecordResponse> records = games.stream().map(g -> {
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
					.isCompleted(g.isCompleted())
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
		}).toList();

		return new PageImpl<>(records, pageable, gamesPage.getTotalElements());
	}

	public AnalysisResponse getAnalysis(Long gameId) {
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

		FeedbackResult feedback = determineFeedback(aimP, burstP, eP, startP, paceP, N,
				totals, myAvgTotal, myPaceStddev);

		AnalysisSummary summary = AnalysisSummary.builder()
				.totalTime(game.getTTotal())
				.globalRank(globalRank)
				.globalPercentile(Math.round(globalPercentile * 10.0) / 10.0)
				.purePhysicalAverage((int) Math.round(myAvgTotal))
				.entryPrecision(game.getTEnterMain())
				.initialSprintSpeed(initialSprint != null ? initialSprint.intValue() : 0)
				.paceDeviation(Math.round(myPaceStddev * 100.0) / 100.0)
				.feedbackCode(feedback.code)
				.feedbackMessage(feedback.message)
				.build();

		return AnalysisResponse.builder()
				.gameId(gameId)
				.totalCourses(totalCourses)
				.isCompleted(game.isCompleted())
				.summary(summary)
				.details(analysisDetails)
				.build();
	}

	private static class FeedbackResult {
		final String code;
		final String message;

		FeedbackResult(String code, String message) {
			this.code = code;
			this.message = message;
		}
	}

	private FeedbackResult determineFeedback(double aimP, double burstP, double eP, double startP,
	                                         double paceP, int N, List<Integer> totals,
	                                         double avgTotal, double paceStddev) {
		// Category 1: Physical Balance (Aiming vs Burst)
		if (aimP <= 30 && burstP <= 30) {
			return new FeedbackResult("GOD_TIER_PHYSICAL",
					"압도적이고 완벽한 피지컬! 에이밍과 팝업 연타 모두 최상위권입니다. 수강신청 실패는 당신의 사전에 없습니다.");
		}
		if (aimP >= 70 && burstP >= 70) {
			return new FeedbackResult("PHYSICAL_UPGRADE_NEEDED",
					"전체적인 피지컬 반응 속도가 아쉽습니다. 꾸준한 연습을 통해 마우스 에임과 키보드 반응 속도를 모두 끌어올려 보세요.");
		}
		if (aimP >= 70 && burstP <= 30) {
			return new FeedbackResult("FAST_BUT_INACCURATE",
					"팝업을 넘기는 손놀림은 최상위권이지만, 마우스 에임이 크게 흔들려 시간을 뺏기고 있습니다. 침착하게 다음 과목을 조준해 보세요.");
		}
		if (aimP > burstP) {
			return new FeedbackResult("SLOW_AIM",
					"팝업 연타 속도에 비해 리스트에서 다음 과목을 찾아 조준하는 에임(Aim) 속도가 상대적으로 지체됩니다. 다음 마우스 위치를 미리 예측하세요!");
		}
		if (burstP >= aimP) {
			return new FeedbackResult("SLOW_BURST",
					"과목 조준은 안정적이지만, 팝업창을 처리하는 연타 반응이 상대적으로 아쉽습니다. 엔터키나 마우스 좌클릭을 더 빠르게 누르는 감각을 익혀보세요.");
		}

		// Category 2: Entry & Start
		if (eP <= 30 && startP <= 30) {
			return new FeedbackResult("PERFECT_ENTRY_START",
					"완벽에 가까운 정각 진입과 압도적인 1순위 과목 선점! 수강신청 도입부의 지배자입니다.");
		}
		if (eP <= 30 && startP >= 70) {
			return new FeedbackResult("ENTRY_MASTER_START_NOVICE",
					"메인방 진입 타이밍은 완벽했으나, 정작 가장 중요한 1순위 과목 클릭에서 크게 머뭇거렸습니다. 진입 후 첫 클릭까지의 동선을 최소화하세요.");
		}
		if (eP >= 70 && startP <= 30) {
			return new FeedbackResult("ENTRY_LATE_START_MASTER",
					"진입 타이밍은 다소 늦었지만 경이로운 반응속도로 1순위 과목을 낚아챘습니다. 시작 알림에 조금만 더 귀를 기울여 진입 속도를 보완해 보세요.");
		}
		if (eP >= 70 && startP > 30) {
			return new FeedbackResult("NEED_FASTER_ENTRY",
					"메인방 진입 속도가 늦어 시작부터 남들보다 불리한 포지션에 놓였습니다. 버튼이 활성화되는 즉시 반응하는 훈련이 필요합니다.");
		}
		if (!(eP <= 30 && startP >= 70) && !(eP >= 70 && startP <= 30) && !(eP >= 70 && startP > 30)) {
			return new FeedbackResult("START_HESITATION",
					"진입 타이밍은 보통 수준으로 무난했으나 1순위 과목을 선점하는 속도가 폭발적이지 못합니다. 가장 치열한 첫 과목에 모든 집중을 쏟으세요!");
		}

		// Category 3: Pace & Focus (N >= 3)
		if (N >= 3) {
			if (paceP <= 30) {
				return new FeedbackResult("MACHINE_LIKE_PACE",
						"기복이 거의 없는 완벽한 페이스! 흔들리지 않는 멘탈로 모든 과목을 기계처럼 정교하게 처리했습니다.");
			}
			if (paceStddev > 0) {
				double panicThreshold = avgTotal + 1.5 * paceStddev;
				boolean hasPanic = totals.stream().anyMatch(t -> t > panicThreshold);
				if (hasPanic) {
					return new FeedbackResult("EASY_PANIC",
							"중간에 가짜 대기열이나 딜레이를 겪은 직후 템포가 무너지는 경향이 있습니다. 어떠한 변수에도 침착하게 다음 과목을 준비하는 멘탈 관리가 필요합니다.");
				}
			}
			int half = N / 2;
			double firstHalfAvg = totals.subList(0, half).stream().mapToInt(Integer::intValue).average().orElse(0);
			double secondHalfAvg = totals.subList(half, N).stream().mapToInt(Integer::intValue).average().orElse(0);
			if (firstHalfAvg - secondHalfAvg >= 100) {
				return new FeedbackResult("STRONG_FINISHER",
						"초반보다 후반부 과목으로 갈수록 오히려 속도가 빨라지는 강력한 뒷심을 보여주었습니다. 초반의 긴장감만 극복해 보세요.");
			}
			if (secondHalfAvg - firstHalfAvg >= 100) {
				return new FeedbackResult("WEAK_FINISHER",
						"시작은 좋았으나 후반부로 갈수록 집중력이 급격히 떨어지는 페이스 저하가 보입니다. 마지막 과목을 끝낼 때까지 긴장의 끈을 놓지 마세요.");
			}
			return new FeedbackResult("FLUCTUATING_PACE",
					"과목별 소요 시간의 기복이 다소 존재합니다. 일정한 리듬을 찾지 못하고 특정 과목에서 당황했을 가능성이 높습니다.");
		}

		return new FeedbackResult("SLOW_BURST",
				"과목 조준은 안정적이지만, 팝업창을 처리하는 연타 반응이 상대적으로 아쉽습니다.");
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
