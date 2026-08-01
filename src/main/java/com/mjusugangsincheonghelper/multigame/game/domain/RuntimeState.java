package com.mjusugangsincheonghelper.multigame.game.domain;

public enum RuntimeState {
	READY,
	PROGRESS,
	CANCELLED;

	public static RuntimeState from(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return valueOf(value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
