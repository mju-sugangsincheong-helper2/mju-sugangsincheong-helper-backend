package com.mjusugangsincheonghelper.multigame.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GameStateTest {

	@Test
	@DisplayName("문자열을 GameState Enum으로 올바르게 변환한다")
	void fromString_success() {
		assertThat(GameState.fromString("WAITING")).isEqualTo(GameState.WAITING);
		assertThat(GameState.fromString("waiting")).isEqualTo(GameState.WAITING);
		assertThat(GameState.fromString("READY")).isEqualTo(GameState.READY);
		assertThat(GameState.fromString("PROGRESS")).isEqualTo(GameState.PROGRESS);
		assertThat(GameState.fromString("ENDED")).isEqualTo(GameState.ENDED);
		assertThat(GameState.fromString("FINALIZE")).isEqualTo(GameState.FINALIZE);
		assertThat(GameState.fromString("CANCELLED")).isEqualTo(GameState.CANCELLED);
	}

	@Test
	@DisplayName("잘못된 문자열이나 null은 null을 반환한다")
	void fromString_invalid() {
		assertThat(GameState.fromString(null)).isNull();
		assertThat(GameState.fromString("")).isNull();
		assertThat(GameState.fromString("   ")).isNull();
		assertThat(GameState.fromString("UNKNOWN")).isNull();
	}

	@ParameterizedTest
	@CsvSource({
			"WAITING, READY, true",
			"WAITING, CANCELLED, true",
			"WAITING, PROGRESS, false",
			"READY, PROGRESS, true",
			"READY, CANCELLED, true",
			"READY, WAITING, false",
			"PROGRESS, ENDED, true",
			"PROGRESS, CANCELLED, true",
			"ENDED, FINALIZE, true",
			"ENDED, CANCELLED, true",
			"FINALIZE, CANCELLED, false",
			"CANCELLED, WAITING, false"
	})
	@DisplayName("상태 전이 유효성을 검증한다")
	void canTransitionTo(GameState current, GameState target, boolean expected) {
		assertThat(current.canTransitionTo(target)).isEqualTo(expected);
	}

	@Test
	@DisplayName("Terminal 상태 검증")
	void isTerminal() {
		assertThat(GameState.FINALIZE.isTerminal()).isTrue();
		assertThat(GameState.CANCELLED.isTerminal()).isTrue();

		assertThat(GameState.WAITING.isTerminal()).isFalse();
		assertThat(GameState.READY.isTerminal()).isFalse();
		assertThat(GameState.PROGRESS.isTerminal()).isFalse();
		assertThat(GameState.ENDED.isTerminal()).isFalse();
	}
}
