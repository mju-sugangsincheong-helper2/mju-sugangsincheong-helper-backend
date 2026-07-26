package com.mjusugangsincheonghelper.singlegame.service;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SingleGameFeedbackEngine {

	public record FeedbackResult(String code, String message) {}

	public FeedbackResult determineFeedback(
			double aimP,
			double burstP,
			double eP,
			double startP,
			double paceP,
			int N,
			List<Integer> totals,
			double avgTotal,
			double paceStddev) {

		// 1. Physical Axis Extreme Rules
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

		// 2. Entry & Start Axis Priority Rules
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

		// 3. Pace & Focus Axis Priority Rules (when N >= 3)
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
			double secondHalfAvg = totals.subList(N - half, N).stream().mapToInt(Integer::intValue).average().orElse(0);
			if (firstHalfAvg - secondHalfAvg >= 100) {
				return new FeedbackResult("STRONG_FINISHER",
						"초반보다 후반부 과목으로 갈수록 오히려 속도가 빨라지는 강력한 뒷심을 보여주었습니다. 초반의 긴장감만 극복해 보세요.");
			}
			if (secondHalfAvg - firstHalfAvg >= 100) {
				return new FeedbackResult("WEAK_FINISHER",
						"시작은 좋았으나 후반부로 갈수록 집중력이 급격히 떨어지는 페이스 저하가 보입니다. 마지막 과목을 끝낼 때까지 긴장의 끈을 놓지 마세요.");
			}
		}

		// 4. Physical Difference Rules
		if (aimP > burstP) {
			return new FeedbackResult("SLOW_AIM",
					"팝업 연타 속도에 비해 리스트에서 다음 과목을 찾아 조준하는 에임(Aim) 속도가 상대적으로 지체됩니다. 다음 마우스 위치를 미리 예측하세요!");
		}
		if (burstP > aimP) {
			return new FeedbackResult("SLOW_BURST",
					"과목 조준은 안정적이지만, 팝업창을 처리하는 연타 반응이 상대적으로 아쉽습니다. 엔터키나 마우스 좌클릭을 더 빠르게 누르는 감각을 익혀보세요.");
		}

		// 5. Fallback Rules
		if (N >= 3 && paceStddev > 0) {
			return new FeedbackResult("FLUCTUATING_PACE",
					"과목별 소요 시간의 기복이 다소 존재합니다. 일정한 리듬을 찾지 못하고 특정 과목에서 당황했을 가능성이 높습니다.");
		}
		if (startP >= 50) {
			return new FeedbackResult("START_HESITATION",
					"진입 타이밍은 보통 수준으로 무난했으나 1순위 과목을 선점하는 속도가 폭발적이지 못합니다. 가장 치열한 첫 과목에 모든 집중을 쏟으세요!");
		}

		return new FeedbackResult("SLOW_BURST",
				"과목 조준은 안정적이지만, 팝업창을 처리하는 연타 반응이 상대적으로 아쉽습니다.");
	}
}
