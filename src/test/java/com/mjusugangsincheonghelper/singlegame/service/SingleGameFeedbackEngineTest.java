package com.mjusugangsincheonghelper.singlegame.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SingleGameFeedbackEngine 단위 테스트")
class SingleGameFeedbackEngineTest {

	private SingleGameFeedbackEngine feedbackEngine;

	@BeforeEach
	void setUp() {
		feedbackEngine = new SingleGameFeedbackEngine();
	}

	@Nested
	@DisplayName("determineFeedback 메서드는")
	class DetermineFeedbackTest {

		@Test
		@DisplayName("피지컬 최상위 조건(aimP<=30, burstP<=30)일 때 GOD_TIER_PHYSICAL을 반환한다")
		void it_returns_god_tier_physical() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					20.0, 20.0, 50.0, 50.0, 50.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("GOD_TIER_PHYSICAL");
		}

		@Test
		@DisplayName("피지컬 저하 조건(aimP>=70, burstP>=70)일 때 PHYSICAL_UPGRADE_NEEDED를 반환한다")
		void it_returns_physical_upgrade_needed() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					75.0, 75.0, 50.0, 50.0, 50.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("PHYSICAL_UPGRADE_NEEDED");
		}

		@Test
		@DisplayName("에임 불량 조건(aimP>=70, burstP<=30)일 때 FAST_BUT_INACCURATE를 반환한다")
		void it_returns_fast_but_inaccurate() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					80.0, 20.0, 50.0, 50.0, 50.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("FAST_BUT_INACCURATE");
		}

		@Test
		@DisplayName("완벽 진입 조건(eP<=30, startP<=30)일 때 PERFECT_ENTRY_START를 반환한다")
		void it_returns_perfect_entry_start() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 20.0, 20.0, 50.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("PERFECT_ENTRY_START");
		}

		@Test
		@DisplayName("진입 완벽 & 스타트 미흡 조건(eP<=30, startP>=70)일 때 ENTRY_MASTER_START_NOVICE를 반환한다")
		void it_returns_entry_master_start_novice() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 20.0, 80.0, 50.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("ENTRY_MASTER_START_NOVICE");
		}

		@Test
		@DisplayName("진입 지연 & 스타트 마스터 조건(eP>=70, startP<=30)일 때 ENTRY_LATE_START_MASTER를 반환한다")
		void it_returns_entry_late_start_master() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 80.0, 20.0, 50.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("ENTRY_LATE_START_MASTER");
		}

		@Test
		@DisplayName("진입 속도 보완 필요 조건(eP>=70, startP>30)일 때 NEED_FASTER_ENTRY를 반환한다")
		void it_returns_need_faster_entry() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 80.0, 50.0, 50.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("NEED_FASTER_ENTRY");
		}

		@Test
		@DisplayName("기계 같은 페이스 조건(paceP<=30, N>=3)일 때 MACHINE_LIKE_PACE를 반환한다")
		void it_returns_machine_like_pace() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 50.0, 50.0, 20.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("MACHINE_LIKE_PACE");
		}

		@Test
		@DisplayName("패닉 발생 조건(paceThreshold 초과)일 때 EASY_PANIC을 반환한다")
		void it_returns_easy_panic() {
			// avgTotal = 1000.0, paceStddev = 200.0 -> panicThreshold = 1300.0
			// totals contains 1500 > 1300 -> EASY_PANIC
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 50.0, 50.0, 50.0, 3, List.of(800, 1500, 700), 1000.0, 200.0);

			assertThat(result.code()).isEqualTo("EASY_PANIC");
		}

		@Test
		@DisplayName("강력한 뒷심 조건(firstHalf - secondHalf >= 100)일 때 STRONG_FINISHER를 반환한다")
		void it_returns_strong_finisher() {
			// N=6: 1~3 avg = 1000, 4~6 avg = 800 -> diff = 200 >= 100 -> STRONG_FINISHER
			SingleGameFeedbackEngine.FeedbackResult result = result = feedbackEngine.determineFeedback(
					50.0, 50.0, 50.0, 50.0, 50.0, 6, List.of(1000, 1000, 1000, 800, 800, 800), 900.0, 100.0);

			assertThat(result.code()).isEqualTo("STRONG_FINISHER");
		}

		@Test
		@DisplayName("N=7 등 홀수 과목일 때 정중앙 과목을 제외하고 STRONG_FINISHER가 판정된다")
		void it_returns_strong_finisher_for_odd_n() {
			// N=7: 1~3 avg = 1000, 4 (middle excluded), 5~7 avg = 800 -> diff = 200 >= 100
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 50.0, 50.0, 50.0, 7, List.of(1000, 1000, 1000, 1000, 800, 800, 800), 914.0, 98.0);

			assertThat(result.code()).isEqualTo("STRONG_FINISHER");
		}

		@Test
		@DisplayName("후반 저하 조건(secondHalf - firstHalf >= 100)일 때 WEAK_FINISHER를 반환한다")
		void it_returns_weak_finisher() {
			// N=6: 1~3 avg = 800, 4~6 avg = 1000 -> diff = 200 >= 100 -> WEAK_FINISHER
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 50.0, 50.0, 50.0, 6, List.of(800, 800, 800, 1000, 1000, 1000), 900.0, 100.0);

			assertThat(result.code()).isEqualTo("WEAK_FINISHER");
		}

		@Test
		@DisplayName("에임 지체 조건(aimP > burstP)일 때 SLOW_AIM을 반환한다")
		void it_returns_slow_aim() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					60.0, 40.0, 50.0, 50.0, 50.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("SLOW_AIM");
		}

		@Test
		@DisplayName("버스트 지체 조건(burstP > aimP)일 때 SLOW_BURST를 반환한다")
		void it_returns_slow_burst() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					40.0, 60.0, 50.0, 50.0, 50.0, 6, List.of(1000, 1000, 1000, 1000, 1000, 1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("SLOW_BURST");
		}

		@Test
		@DisplayName("페이스 기복 잔여 조건(N>=3, paceStddev>0)일 때 FLUCTUATING_PACE를 반환한다")
		void it_returns_fluctuating_pace() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 50.0, 50.0, 50.0, 3, List.of(990, 1010, 1000), 1000.0, 10.0);

			assertThat(result.code()).isEqualTo("FLUCTUATING_PACE");
		}

		@Test
		@DisplayName("스타트 주저 조건(startP>=50)일 때 START_HESITATION을 반환한다")
		void it_returns_start_hesitation() {
			SingleGameFeedbackEngine.FeedbackResult result = feedbackEngine.determineFeedback(
					50.0, 50.0, 50.0, 60.0, 0.0, 1, List.of(1000), 1000.0, 0.0);

			assertThat(result.code()).isEqualTo("START_HESITATION");
		}
	}
}
