package com.mjusugangsincheonghelper.multigame.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GameStatusResolverTest {

	/** 기본 미운영 시간대(한국 표준시 02:00+09:00 ~ 05:00+09:00)로 생성된 리졸버. yml 미설정 시의 기본값과 동일하다. */
	private static final GameStatusResolver DEFAULT = new GameStatusResolver();

	/** UTC 21:M:S — 페이즈(분/초) 판정용. 한국 오프셋이 정수 시간이므로 UTC/KST 분·초가 동일하다. */
	private static Instant at(int minute, int second) {
		return Instant.parse("2026-08-01T21:%02d:%02dZ".formatted(minute, second));
	}

	/** 한국 표준시(KST) 벽시계 M:H:S 에 해당하는 순간. 미운영 시간대 판정용. */
	private static Instant at(int minute, int second, int kstHour) {
		int utcHour = Math.floorMod(kstHour - 9, 24);
		return Instant.parse("2026-08-01T%02d:%02d:%02dZ".formatted(utcHour, minute, second));
	}

	// ---------------------------------------------------------------------
	// 미운영 시간대 (CLOSED)
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("미운영 시간대 (기본 02:00+09:00 ~ 05:00+09:00)")
	class ClosedWindow {

		@Test
		void closesFromStartInclusive() {
			assertThat(DEFAULT.resolve(at(0, 0, 2), RuntimeState.PROGRESS)).isEqualTo(GameStatus.CLOSED);
		}

		@Test
		void closesUntilEndExclusive() {
			assertThat(DEFAULT.resolve(at(59, 59, 4), RuntimeState.PROGRESS)).isEqualTo(GameStatus.CLOSED);
		}

		@Test
		void opensAtStartBoundaryExclusive() {
			assertThat(DEFAULT.resolve(at(59, 59, 1), null)).isNotEqualTo(GameStatus.CLOSED);
			assertThat(DEFAULT.resolve(at(0, 0, 5), null)).isNotEqualTo(GameStatus.CLOSED);
		}

		@Test
		void closedWindowIgnoresRedisState() {
			for (RuntimeState state : RuntimeState.values()) {
				assertThat(DEFAULT.resolve(at(30, 0, 3), state)).isEqualTo(GameStatus.CLOSED);
			}
		}
	}

	@Nested
	@DisplayName("미운영 시간대 (yml 주입 — 자정을 넘기는 설정, 비활성화)")
	class CustomClosedWindow {

		@Test
		void closesAcrossMidnight() {
			GameStatusResolver resolver = new GameStatusResolver(
					OffsetTime.of(23, 0, 0, 0, ZoneOffset.ofHours(9)),
					OffsetTime.of(1, 0, 0, 0, ZoneOffset.ofHours(9)));
			assertThat(resolver.resolve(at(30, 0, 23), null)).isEqualTo(GameStatus.CLOSED);
			assertThat(resolver.resolve(at(59, 59, 23), null)).isEqualTo(GameStatus.CLOSED);
			assertThat(resolver.resolve(at(0, 0, 0), null)).isEqualTo(GameStatus.CLOSED);
			assertThat(resolver.resolve(at(59, 59, 0), null)).isEqualTo(GameStatus.CLOSED);
		}

		@Test
		void opensOutsideMidnightWindow() {
			GameStatusResolver resolver = new GameStatusResolver(
					OffsetTime.of(23, 0, 0, 0, ZoneOffset.ofHours(9)),
					OffsetTime.of(1, 0, 0, 0, ZoneOffset.ofHours(9)));
			assertThat(resolver.resolve(at(59, 59, 22), null)).isNotEqualTo(GameStatus.CLOSED);
			assertThat(resolver.resolve(at(0, 0, 1), null)).isNotEqualTo(GameStatus.CLOSED);
		}

		@Test
		void disablesClosedWindowWhenStartEqualsEnd() {
			// start-close == end-close 이면 미운영 시간대 없음 (24시간 운영)
			GameStatusResolver resolver = new GameStatusResolver(
					OffsetTime.of(0, 0, 0, 0, ZoneOffset.ofHours(9)),
					OffsetTime.of(0, 0, 0, 0, ZoneOffset.ofHours(9)));
			for (int hour : new int[] {0, 2, 5, 12, 23}) {
				assertThat(resolver.resolve(at(0, 0, hour), null)).isNotEqualTo(GameStatus.CLOSED);
			}
		}
	}

	// ---------------------------------------------------------------------
	// 대기 (WAITING): T-9m ~ T-5s  (21:01:00 ~ 21:09:54, T = 21:10)
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("대기 구간 (T-9m ~ T-5s)")
	class WaitingPhase {

		@Test
		void waitsFromTPlusOneMinute() {
			// ENDED(T+30s ~ T+1m)가 끝나는 즉시 다음 게임 대기가 시작된다.
			assertThat(DEFAULT.resolve(at(1, 0), null)).isEqualTo(GameStatus.WAITING);
		}

		@Test
		void waitsThroughTheWholeFirstMinute() {
			// 회귀 테스트: 21:01:xx 는 ENDED가 아니라 WAITING 이어야 한다.
			assertThat(DEFAULT.resolve(at(1, 30), null)).isEqualTo(GameStatus.WAITING);
			assertThat(DEFAULT.resolve(at(1, 59), null)).isEqualTo(GameStatus.WAITING);
		}

		@Test
		void waitsUntilFiveSecondsBeforeTheMark() {
			assertThat(DEFAULT.resolve(at(9, 54), null)).isEqualTo(GameStatus.WAITING);
		}

		@Test
		void waitingIgnoresRedisState() {
			assertThat(DEFAULT.resolve(at(4, 30), RuntimeState.CANCELLED)).isEqualTo(GameStatus.WAITING);
			assertThat(DEFAULT.resolve(at(4, 30), RuntimeState.READY)).isEqualTo(GameStatus.WAITING);
		}
	}

	// ---------------------------------------------------------------------
	// 준비 (READY/STARTING): T-5s ~ T  (21:09:55 ~ 21:09:59)
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("준비 구간 (T-5s ~ T)")
	class ReadyPhase {

		@Test
		void resolvesReadyOnlyInTheFinalFiveSeconds() {
			assertThat(DEFAULT.resolve(at(9, 55), RuntimeState.READY)).isEqualTo(GameStatus.READY);
			assertThat(DEFAULT.resolve(at(9, 59), RuntimeState.READY)).isEqualTo(GameStatus.READY);
		}

		@Test
		void startingWhenRedisStateNotYetReady() {
			// Cron 지연 등으로 Redis 상태가 없거나 READY가 아니면 STARTING
			assertThat(DEFAULT.resolve(at(9, 55), null)).isEqualTo(GameStatus.STARTING);
			assertThat(DEFAULT.resolve(at(9, 55), RuntimeState.PROGRESS)).isEqualTo(GameStatus.STARTING);
		}

		@Test
		void cancelledTakesPriorityDuringReadyPhase() {
			assertThat(DEFAULT.resolve(at(9, 55), RuntimeState.CANCELLED)).isEqualTo(GameStatus.CANCELLED);
		}
	}

	// ---------------------------------------------------------------------
	// 진행 (PROGRESS/STARTING): T ~ T+30s  (21:10:00 ~ 21:10:29)
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("진행 구간 (T ~ T+30s)")
	class ProgressPhase {

		@Test
		void progressesFromTheMark() {
			assertThat(DEFAULT.resolve(at(10, 0), RuntimeState.PROGRESS)).isEqualTo(GameStatus.PROGRESS);
			assertThat(DEFAULT.resolve(at(10, 29), RuntimeState.PROGRESS)).isEqualTo(GameStatus.PROGRESS);
		}

		@Test
		void startingWhenRedisStateNotYetProgress() {
			assertThat(DEFAULT.resolve(at(10, 0), null)).isEqualTo(GameStatus.STARTING);
			assertThat(DEFAULT.resolve(at(10, 0), RuntimeState.READY)).isEqualTo(GameStatus.STARTING);
		}

		@Test
		void cancelledTakesPriorityDuringProgressPhase() {
			assertThat(DEFAULT.resolve(at(10, 0), RuntimeState.CANCELLED)).isEqualTo(GameStatus.CANCELLED);
		}
	}

	// ---------------------------------------------------------------------
	// 종료 (ENDED): T+30s ~ T+1m  (21:10:30 ~ 21:10:59)
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("종료 구간 (T+30s ~ T+1m)")
	class EndedPhase {

		@Test
		void endsThirtySecondsAfterTheMark() {
			assertThat(DEFAULT.resolve(at(10, 30), RuntimeState.PROGRESS)).isEqualTo(GameStatus.ENDED);
			assertThat(DEFAULT.resolve(at(10, 59), RuntimeState.PROGRESS)).isEqualTo(GameStatus.ENDED);
		}

		@Test
		void endedIgnoresRedisState() {
			assertThat(DEFAULT.resolve(at(10, 45), RuntimeState.CANCELLED)).isEqualTo(GameStatus.ENDED);
			assertThat(DEFAULT.resolve(at(10, 45), RuntimeState.READY)).isEqualTo(GameStatus.ENDED);
			assertThat(DEFAULT.resolve(at(10, 45), null)).isEqualTo(GameStatus.ENDED);
		}
	}

	// ---------------------------------------------------------------------
	// 전체 10분 라이프사이클 경계 매트릭스
	// ---------------------------------------------------------------------

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("tenMinuteLifecycle")
	@DisplayName("10분 라이프사이클 전체 경계 매트릭스")
	void tenMinuteLifecycle(Instant now, GameStatus expected) {
		assertThat(DEFAULT.resolve(now, RuntimeState.PROGRESS)).isEqualTo(expected);
	}

	static Stream<Arguments> tenMinuteLifecycle() {
		// T=21:10 게임 기준 한 사이클 (RuntimeState.PROGRESS 전제)
		return Stream.of(
				// 직전 게임(T=21:00) 종료 후반부
				Arguments.of(at(0, 29), GameStatus.PROGRESS),   // T=21:00, 진행 마지막 1초
				Arguments.of(at(0, 30), GameStatus.ENDED),      // T=21:00, 종료 시작
				Arguments.of(at(0, 59), GameStatus.ENDED),      // T=21:00, 종료 마지막 1초
				// 다음 게임(T=21:10) 대기
				Arguments.of(at(1, 0), GameStatus.WAITING),     // T+1m 경계 — WAITING 시작
				Arguments.of(at(1, 30), GameStatus.WAITING),    // 회귀: :01분은 WAITING
				Arguments.of(at(4, 30), GameStatus.WAITING),    // 대기 중간
				Arguments.of(at(9, 54), GameStatus.WAITING),    // T-5s 직전
				// 준비 (T-5s ~ T)
				Arguments.of(at(9, 55), GameStatus.STARTING),   // READY가 아니라 STARTING (state=PROGRESS 이므로)
				Arguments.of(at(9, 59), GameStatus.STARTING),   // 준비 마지막 1초
				// 진행 (T ~ T+30s)
				Arguments.of(at(10, 0), GameStatus.PROGRESS),   // T 정각 — 진행 시작
				Arguments.of(at(10, 29), GameStatus.PROGRESS),  // 진행 마지막 1초
				// 종료 (T+30s ~ T+1m)
				Arguments.of(at(10, 30), GameStatus.ENDED),     // 종료 시작
				Arguments.of(at(10, 59), GameStatus.ENDED),     // 종료 마지막 1초
				// 다음 사이클 대기 시작
				Arguments.of(at(11, 0), GameStatus.WAITING),    // T+1m 경계 — WAITING 시작
				Arguments.of(at(11, 59), GameStatus.WAITING));  // :01분 전체가 WAITING
	}

	// ---------------------------------------------------------------------
	// 상태별 최종 판정 (Time x Redis 상태 매트릭스)
	// ---------------------------------------------------------------------

	@ParameterizedTest(name = "{0} + {1} -> {2}")
	@MethodSource("stateMatrix")
	@DisplayName("시간 x Redis 상태 최종 판정 매트릭스")
	void stateMatrix(Instant now, RuntimeState redisState, GameStatus expected) {
		assertThat(DEFAULT.resolve(now, redisState)).isEqualTo(expected);
	}

	static Stream<Arguments> stateMatrix() {
		return Stream.of(
				// CLOSED: Redis 상태와 무관
				Arguments.of(at(30, 0, 3), null, GameStatus.CLOSED),
				Arguments.of(at(30, 0, 3), RuntimeState.PROGRESS, GameStatus.CLOSED),
				// WAITING: Redis 상태와 무관
				Arguments.of(at(4, 30), null, GameStatus.WAITING),
				Arguments.of(at(4, 30), RuntimeState.CANCELLED, GameStatus.WAITING),
				// READY_PHASE
				Arguments.of(at(9, 58), RuntimeState.CANCELLED, GameStatus.CANCELLED),
				Arguments.of(at(9, 58), RuntimeState.READY, GameStatus.READY),
				Arguments.of(at(9, 58), null, GameStatus.STARTING),
				Arguments.of(at(9, 58), RuntimeState.PROGRESS, GameStatus.STARTING),
				// PROGRESS_PHASE
				Arguments.of(at(10, 15), RuntimeState.CANCELLED, GameStatus.CANCELLED),
				Arguments.of(at(10, 15), RuntimeState.PROGRESS, GameStatus.PROGRESS),
				Arguments.of(at(10, 15), null, GameStatus.STARTING),
				Arguments.of(at(10, 15), RuntimeState.READY, GameStatus.STARTING),
				// ENDED: Redis 상태와 무관
				Arguments.of(at(10, 45), RuntimeState.PROGRESS, GameStatus.ENDED),
				Arguments.of(at(10, 45), RuntimeState.CANCELLED, GameStatus.ENDED),
				Arguments.of(at(10, 45), null, GameStatus.ENDED));
	}

	// ---------------------------------------------------------------------
	// RoundTime 과의 정합성 (상태가 어떤 라운드에 귀속되는지)
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("대기 구간의 타겟 라운드는 다음 10분 마크이다")
	void waitingTargetsTheNextRound() {
		Instant now = Instant.parse("2026-08-01T12:04:30Z");
		assertThat(RoundTime.target(now)).isEqualTo("20260801121000");
		assertThat(DEFAULT.resolve(now, null)).isEqualTo(GameStatus.WAITING);
	}

	@Test
	@DisplayName("진행/종료 구간의 타겟 라운드는 직전 10분 마크이다")
	void progressTargetsTheCurrentRound() {
		Instant now = Instant.parse("2026-08-01T12:00:45Z");
		assertThat(RoundTime.target(now)).isEqualTo("20260801120000");
		assertThat(DEFAULT.resolve(now, RuntimeState.PROGRESS)).isEqualTo(GameStatus.ENDED);
	}
}
