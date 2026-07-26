package com.mjusugangsincheonghelper.multigame.session.domain;

import java.util.Collections;
import java.util.Set;

public enum GameState {
	WAITING,
	READY,
	PROGRESS,
	ENDED,
	FINALIZE,
	CANCELLED;

	private Set<GameState> nextValidStates;

	static {
		WAITING.nextValidStates = Set.of(READY, CANCELLED);
		READY.nextValidStates = Set.of(PROGRESS, CANCELLED);
		PROGRESS.nextValidStates = Set.of(ENDED, CANCELLED);
		ENDED.nextValidStates = Set.of(FINALIZE, CANCELLED);
		FINALIZE.nextValidStates = Collections.emptySet();
		CANCELLED.nextValidStates = Collections.emptySet();
	}

	public boolean canTransitionTo(GameState targetState) {
		if (targetState == null) {
			return false;
		}
		return nextValidStates.contains(targetState);
	}

	public boolean isTerminal() {
		return this == FINALIZE || this == CANCELLED;
	}

	public static GameState fromString(String stateStr) {
		if (stateStr == null || stateStr.isBlank()) {
			return null;
		}
		try {
			return GameState.valueOf(stateStr.toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
