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
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.DeptRankInfo;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.DetailEvent;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.PopulationStats;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.RankInfo;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.RecordInfo;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.TimelineEvent;
import com.mjusugangsincheonghelper.singlegame.dto.cache.FeedbackQuantileTable;
import com.mjusugangsincheonghelper.singlegame.dto.cache.QuantileBand;
import com.mjusugangsincheonghelper.singlegame.dto.cache.SequenceQuantileTable;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 싱글게임 분석 전담. 원본(game, details)은 DB에서, 분위수는 캐시에서.
 *
 * <p>원칙: {@code SequenceQuantileTable(분위수 7점)}이 유일한 모집단 원천.
 * 내부 백분위순위/등급 근사는 7점 전구간 보간으로 계산하고,
 * 응답 노출은 기존 4점({@code p10/p30/p50/p70}) 그대로 유지한다.
 * 응답 자체는 캐시하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SingleGameAnalysisService {

	private final SingleGameRepository singleGameRepository;
	private final SingleGameDetailRepository singleGameDetailRepository;
	private final MemberRepository memberRepository;
	private final SingleGameStatsService singleGameStatsService;
	private final SingleGameFeedbackEngine feedbackEngine;
	private final SingleGameQuantileInterpolator quantileInterpolator;

	public AnalysisResponse analyze(long gameId, Long viewerId) {
		SingleGameEntity game = singleGameRepository.findById(gameId)
				.orElseThrow(() -> new BaseException(ErrorCode.SINGLEGAME_GAME_NOT_FOUND));

		List<SingleGameDetailEntity> attempts = singleGameDetailRepository
				.findByGameIdOrderBySequenceAsc(gameId);

		int totalCourses = game.getTotalCourses();

		boolean isOwner = viewerId != null && game.getMemberId().equals(viewerId);
		Member gameOwner = memberRepository.findById(game.getMemberId()).orElse(null);
		String ownerDepartment = gameOwner != null ? gameOwner.getDepartment() : null;
		if (ownerDepartment != null && ownerDepartment.isBlank()) {
			ownerDepartment = null;
		}

		Member viewer = viewerId != null ? memberRepository.findById(viewerId).orElse(null) : null;
		boolean viewerCanSeeDepartment = viewer != null && viewer.getRole() != Member.Role.GUEST;
		boolean isOwnerMember = gameOwner != null && gameOwner.getRole() != Member.Role.GUEST;

		SequenceQuantileTable globalQuantiles = safeGlobalQuantiles(totalCourses);
		long headcount = singleGameStatsService.getHeadCount(totalCourses);
		double entryRank = quantileInterpolator.estimatePercentileRank(
				game.getTEnterMain(), globalQuantiles.getEnterMain());

		int betterCount = (int) singleGameRepository.countBetterPersons(totalCourses, game.getTTotal());
		RankInfo globalRank = buildPersonRank(betterCount + 1, headcount);

		DeptRankInfo departmentRank = null;
		SequenceQuantileTable departmentQuantiles = null;
		if (viewerCanSeeDepartment && ownerDepartment != null) {
			departmentQuantiles = safeDepartmentQuantiles(totalCourses, ownerDepartment);
			long departmentHeadcount = singleGameStatsService.getDeptHeadCount(totalCourses, ownerDepartment);
			int deptBetterCount = (int) singleGameRepository.countBetterDeptPersons(totalCourses, ownerDepartment,
					game.getTTotal());
			departmentRank = buildDepartmentRank(ownerDepartment, deptBetterCount + 1, departmentHeadcount);
		}

		List<DetailEvent> detailRows = buildDetailRows(game, attempts, globalQuantiles, departmentQuantiles,
				entryRank, viewerCanSeeDepartment);

		var feedbacks = viewerCanSeeDepartment
				? buildFeedbacks(attempts, entryRank, safeFeedbackQuantiles(totalCourses))
				: null;

		return AnalysisResponse.builder()
				.gameId(gameId)
				.owner(isOwner)
				.member(isOwnerMember)
				.totalCourses(totalCourses)
				.record(RecordInfo.builder()
						.tTotal(game.getTTotal())
						.tEnterMain(game.getTEnterMain())
						.completed(game.isCompleted())
						.createdAt(game.getCreatedAt())
						.build())
				.globalRank(globalRank)
				.departmentRank(departmentRank)
				.globalTimeline(buildGlobalTimeline(detailRows))
				.departmentTimeline(
						viewerCanSeeDepartment && departmentQuantiles != null ? buildDepartmentTimeline(detailRows)
								: null)
				.detail(detailRows)
				.feedbacks(feedbacks)
				.build();
	}

	private RankInfo buildPersonRank(int personRank, long headcount) {
		double percentileRank = headcount > 0 ? (double) (personRank - 1) / headcount * 100 : 0;
		return RankInfo.builder()
				.rank(personRank)
				.totalPersons(headcount)
				.percentile(quantileInterpolator.roundToOneDecimal(percentileRank))
				.build();
	}

	private DeptRankInfo buildDepartmentRank(String ownerDepartment, int personRank, long headcount) {
		double percentileRank = headcount > 0 ? (double) (personRank - 1) / headcount * 100 : 0;
		return DeptRankInfo.builder()
				.department(ownerDepartment)
				.rank(personRank)
				.totalPersons(headcount)
				.percentile(quantileInterpolator.roundToOneDecimal(percentileRank))
				.build();
	}

	private List<TimelineEvent> buildGlobalTimeline(List<DetailEvent> detailRows) {
		return detailRows.stream()
				.map(d -> TimelineEvent.builder()
						.sequence(d.getSequence())
						.type(d.getType())
						.label(d.getLabel())
						.durationMs(d.getDurationMs())
						.population(d.getGlobalPopulation())
						.build())
				.toList();
	}

	private List<TimelineEvent> buildDepartmentTimeline(List<DetailEvent> detailRows) {
		return detailRows.stream()
				.map(d -> TimelineEvent.builder()
						.sequence(d.getSequence())
						.type(d.getType())
						.label(d.getLabel())
						.durationMs(d.getDurationMs())
						.population(d.getDepartmentPopulation())
						.build())
				.toList();
	}

	private List<DetailEvent> buildDetailRows(SingleGameEntity game, List<SingleGameDetailEntity> attempts,
			SequenceQuantileTable globalQuantiles, SequenceQuantileTable departmentQuantilesOrNull,
			double entryRank, boolean viewerCanSeeDepartment) {
		List<DetailEvent> rows = new ArrayList<>();

		rows.add(buildDetailRow(0, "ENTRY", "메인방 진입", game.getTEnterMain(),
				globalQuantiles.getEnterMain(),
				departmentQuantilesOrNull != null ? departmentQuantilesOrNull.getEnterMain() : null,
				entryRank, viewerCanSeeDepartment));

		for (SingleGameDetailEntity attempt : attempts) {
			int sequence = attempt.getSequence();
			rows.add(buildDetailRow(sequence, "AIM", sequence + "순위 과목 조준", attempt.getTClickCourse(),
					globalQuantiles.quantileOf(sequence, SegmentMetric.AIM),
					departmentQuantilesOrNull != null
							? departmentQuantilesOrNull.quantileOf(sequence, SegmentMetric.AIM) : null,
					-1, viewerCanSeeDepartment));
			rows.add(buildDetailRow(sequence, "CONFIRM", "신청 확인", attempt.getTClickYes(),
					globalQuantiles.quantileOf(sequence, SegmentMetric.CONFIRM),
					departmentQuantilesOrNull != null
							? departmentQuantilesOrNull.quantileOf(sequence, SegmentMetric.CONFIRM) : null,
					-1, viewerCanSeeDepartment));
			rows.add(buildDetailRow(sequence, "COMPLETE", "완료 확인", attempt.getTClickOk(),
					globalQuantiles.quantileOf(sequence, SegmentMetric.COMPLETE),
					departmentQuantilesOrNull != null
							? departmentQuantilesOrNull.quantileOf(sequence, SegmentMetric.COMPLETE) : null,
					-1, viewerCanSeeDepartment));
		}

		return rows;
	}

	private DetailEvent buildDetailRow(int sequence, String type, String label, int durationMs,
			QuantileBand globalBand, QuantileBand departmentBandOrNull,
			double precomputedRank, boolean viewerCanSeeDepartment) {
		double percentileRank = precomputedRank >= 0
				? precomputedRank
				: quantileInterpolator.estimatePercentileRank(durationMs, globalBand);

		return DetailEvent.builder()
				.sequence(sequence)
				.type(type)
				.label(label)
				.durationMs(durationMs)
				.percentile(quantileInterpolator.roundToOneDecimal(percentileRank))
				.grade(viewerCanSeeDepartment ? quantileInterpolator.assignGrade(percentileRank) : null)
				.globalPopulation(toPopulationStats(globalBand))
				.departmentPopulation(viewerCanSeeDepartment ? toPopulationStats(departmentBandOrNull) : null)
				.build();
	}

	/**
	 * 응답 노출용 4점 변환. 내부 계산은 7점 밴드로 하고,
	 * 응답에는 기존 구조 그대로 p10/p30/p50/p70만 내린다.
	 */
	private PopulationStats toPopulationStats(QuantileBand band) {
		if (band == null || band.isEmpty()) {
			return null;
		}
		return PopulationStats.builder()
				.p10(band.getQ10()).p30(band.getQ30()).p50(band.getQ50()).p70(band.getQ70())
				.build();
	}

	private AnalysisResponse.FeedbacksResponse buildFeedbacks(List<SingleGameDetailEntity> attempts,
			double entryRank, FeedbackQuantileTable feedbackQuantiles) {
		if (feedbackQuantiles == null) {
			feedbackQuantiles = FeedbackQuantileTable.empty();
		}
		int attemptCount = attempts.size();
		double aimAvg = attempts.stream().mapToInt(SingleGameDetailEntity::getTClickCourse).average().orElse(0);
		double confirmAvg = attempts.stream().mapToInt(SingleGameDetailEntity::getTClickYes).average().orElse(0);
		double completeAvg = attempts.stream().mapToInt(SingleGameDetailEntity::getTClickOk).average().orElse(0);
		double burstAvg = confirmAvg + completeAvg;
		List<Integer> attemptTotals = attempts.stream()
				.map(d -> d.getTClickCourse() + d.getTClickYes() + d.getTClickOk()).toList();
		double attemptMean = attemptTotals.stream().mapToInt(Integer::intValue).average().orElse(0);

		double aimRank = quantileInterpolator.estimatePercentileRank(
				(int) Math.round(aimAvg), feedbackQuantiles.getAimAvg());
		double burstRank = quantileInterpolator.estimatePercentileRank(
				(int) Math.round(burstAvg), feedbackQuantiles.getBurstAvg());
		double startRank = attemptCount > 0 ? quantileInterpolator.estimatePercentileRank(
				attemptTotals.get(0), feedbackQuantiles.getFirstTotal()) : 0;
		double paceRank = 0;
		double paceStddev = 0;
		if (attemptCount >= 3) {
			double variance = attemptTotals.stream().mapToDouble(t -> Math.pow(t - attemptMean, 2)).sum()
					/ attemptCount;
			paceStddev = Math.sqrt(variance);
			paceRank = quantileInterpolator.estimatePercentileRank(
					(int) Math.round(paceStddev), feedbackQuantiles.getPaceStd());
		}

		return feedbackEngine.determineFeedbacks(aimRank, burstRank, entryRank, startRank, paceRank,
				attemptCount, attemptTotals, attemptMean, paceStddev);
	}

	private SequenceQuantileTable safeGlobalQuantiles(int totalCourses) {
		try {
			SequenceQuantileTable table = singleGameStatsService.getGlobalSegmentQuantiles(totalCourses);
			return table != null ? table : SequenceQuantileTable.empty();
		} catch (Exception e) {
			return SequenceQuantileTable.empty();
		}
	}

	private SequenceQuantileTable safeDepartmentQuantiles(int totalCourses, String department) {
		try {
			SequenceQuantileTable table = singleGameStatsService.getDepartmentSegmentQuantiles(totalCourses,
					department);
			return table != null ? table : SequenceQuantileTable.empty();
		} catch (Exception e) {
			return SequenceQuantileTable.empty();
		}
	}

	private FeedbackQuantileTable safeFeedbackQuantiles(int totalCourses) {
		try {
			FeedbackQuantileTable table = singleGameStatsService.getFeedbackQuantiles(totalCourses);
			return table != null ? table : FeedbackQuantileTable.empty();
		} catch (Exception e) {
			return FeedbackQuantileTable.empty();
		}
	}
}
