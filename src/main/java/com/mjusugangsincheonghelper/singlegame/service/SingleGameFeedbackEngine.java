package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.FeedbackItem;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse.FeedbacksResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SingleGameFeedbackEngine {

	public record AxisResult(String code, String message, String axis, int priority) {}

	public FeedbacksResponse determineFeedbacks(
			double aimP,
			double burstP,
			double eP,
			double startP,
			double paceP,
			int N,
			List<Integer> totals,
			double avgTotal,
			double paceStddev) {

		AxisResult physical = evaluatePhysical(aimP, burstP);
		AxisResult entryStart = evaluateEntryStart(eP, startP);
		AxisResult pace = N >= 3 ? evaluatePace(paceP, totals, avgTotal, paceStddev) : null;

		AxisResult[] candidates = pace != null
				? new AxisResult[]{physical, entryStart, pace}
				: new AxisResult[]{physical, entryStart};

		AxisResult primary = candidates[0];
		AxisResult secondary = candidates.length > 1 ? candidates[1] : null;
		for (int i = 1; i < candidates.length; i++) {
			if (candidates[i].priority() < primary.priority()) {
				secondary = primary;
				primary = candidates[i];
			} else if (secondary == null || candidates[i].priority() < secondary.priority()) {
				secondary = candidates[i];
			}
		}

		return FeedbacksResponse.builder()
				.primary(toFeedbackItem(primary))
				.secondary(secondary != null ? toFeedbackItem(secondary) : null)
				.build();
	}

	private AxisResult evaluatePhysical(double aimP, double burstP) {
		if (aimP <= 30 && burstP <= 30) {
			return new AxisResult("GOD_TIER_PHYSICAL",
					"압도적이고 완벽한 피지컬! 에이밍과 팝업 연타 모두 최상위권입니다. 수강신청 실패는 당신의 사전에 없습니다.",
					"PHYSICAL", 1);
		}
		if (aimP >= 70 && burstP >= 70) {
			return new AxisResult("PHYSICAL_UPGRADE_NEEDED",
					"전체적인 피지컬 반응 속도가 아쉽습니다. 꾸준한 연습을 통해 마우스 에임과 키보드 반응 속도를 모두 끌어올려 보세요.",
					"PHYSICAL", 2);
		}
		if (aimP >= 70 && burstP <= 30) {
			return new AxisResult("FAST_BUT_INACCURATE",
					"팝업을 넘기는 손놀림은 최상위권이지만, 마우스 에임이 크게 흔들려 시간을 뺏기고 있습니다. 침착하게 다음 과목을 조준해 보세요.",
					"PHYSICAL", 3);
		}
		if (aimP > burstP) {
			return new AxisResult("SLOW_AIM",
					"팝업 연타 속도에 비해 리스트에서 다음 과목을 찾아 조준하는 에임(Aim) 속도가 상대적으로 지체됩니다. 다음 마우스 위치를 미리 예측하세요!",
					"PHYSICAL", 4);
		}
		return new AxisResult("SLOW_BURST",
				"과목 조준은 안정적이지만, 팝업창을 처리하는 연타 반응이 상대적으로 아쉽습니다. 엔터키나 마우스 좌클릭을 더 빠르게 누르는 감각을 익혀보세요.",
				"PHYSICAL", 5);
	}

	private AxisResult evaluateEntryStart(double eP, double startP) {
		if (eP <= 30 && startP <= 30) {
			return new AxisResult("PERFECT_ENTRY_START",
					"완벽에 가까운 정각 진입과 압도적인 1순위 과목 선점! 수강신청 도입부의 지배자입니다.",
					"ENTRY_START", 1);
		}
		if (eP <= 30 && startP >= 70) {
			return new AxisResult("ENTRY_MASTER_START_NOVICE",
					"메인방 진입 타이밍은 완벽했으나, 정작 가장 중요한 1순위 과목 클릭에서 크게 머뭇거렸습니다. 진입 후 첫 클릭까지의 동선을 최소화하세요.",
					"ENTRY_START", 2);
		}
		if (eP >= 70 && startP <= 30) {
			return new AxisResult("ENTRY_LATE_START_MASTER",
					"진입 타이밍은 다소 늦었지만 경이로운 반응속도로 1순위 과목을 낚아챘습니다. 시작 알림에 조금만 더 귀를 기울여 진입 속도를 보완해 보세요.",
					"ENTRY_START", 3);
		}
		if (eP >= 70 && startP > 30) {
			return new AxisResult("NEED_FASTER_ENTRY",
					"메인방 진입 속도가 늦어 시작부터 남들보다 불리한 포지션에 놓였습니다. 버튼이 활성화되는 즉시 반응하는 훈련이 필요합니다.",
					"ENTRY_START", 4);
		}
		return new AxisResult("START_HESITATION",
				"진입 타이밍은 보통 수준으로 무난했으나 1순위 과목을 선점하는 속도가 폭발적이지 못합니다. 가장 치열한 첫 과목에 모든 집중을 쏟으세요!",
				"ENTRY_START", 5);
	}

	private AxisResult evaluatePace(double paceP, List<Integer> totals, double avgTotal, double paceStddev) {
		if (paceP <= 30) {
			return new AxisResult("MACHINE_LIKE_PACE",
					"기복이 거의 없는 완벽한 페이스! 흔들리지 않는 멘탈로 모든 과목을 기계처럼 정교하게 처리했습니다.",
					"PACE", 1);
		}
		if (paceStddev > 0) {
			double panicThreshold = avgTotal + 1.5 * paceStddev;
			boolean hasPanic = totals.stream().anyMatch(t -> t > panicThreshold);
			if (hasPanic) {
				return new AxisResult("EASY_PANIC",
						"중간에 가짜 대기열이나 딜레이를 겪은 직후 템포가 무너지는 경향이 있습니다. 어떠한 변수에도 침착하게 다음 과목을 준비하는 멘탈 관리가 필요합니다.",
						"PACE", 2);
			}
		}
		int N = totals.size();
		int half = N / 2;
		double firstHalfAvg = totals.subList(0, half).stream().mapToInt(Integer::intValue).average().orElse(0);
		double secondHalfAvg = totals.subList(N - half, N).stream().mapToInt(Integer::intValue).average().orElse(0);
		if (firstHalfAvg - secondHalfAvg >= 100) {
			return new AxisResult("STRONG_FINISHER",
					"초반보다 후반부 과목으로 갈수록 오히려 속도가 빨라지는 강력한 뒷심을 보여주었습니다. 초반의 긴장감만 극복해 보세요.",
					"PACE", 3);
		}
		if (secondHalfAvg - firstHalfAvg >= 100) {
			return new AxisResult("WEAK_FINISHER",
					"시작은 좋았으나 후반부로 갈수록 집중력이 급격히 떨어지는 페이스 저하가 보입니다. 마지막 과목을 끝낼 때까지 긴장의 끈을 놓지 마세요.",
					"PACE", 4);
		}
		return new AxisResult("FLUCTUATING_PACE",
				"과목별 소요 시간의 기복이 다소 존재합니다. 일정한 리듬을 찾지 못하고 특정 과목에서 당황했을 가능성이 높습니다.",
				"PACE", 5);
	}

	private FeedbackItem toFeedbackItem(AxisResult result) {
		return FeedbackItem.builder()
				.code(result.code())
				.message(result.message())
				.axis(result.axis())
				.build();
	}
}
