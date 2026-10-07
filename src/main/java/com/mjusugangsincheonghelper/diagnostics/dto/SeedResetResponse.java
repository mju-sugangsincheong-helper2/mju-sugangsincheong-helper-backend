package com.mjusugangsincheonghelper.diagnostics.dto;

/**
 * dev 전용 시드 초기화 결과. {@code SEED_} 접두 멤버와 그 게임·디테일만 삭제한다.
 */
public record SeedResetResponse(
		int deletedGames,
		int deletedDetails,
		int deletedMembers,
		double elapsedMs
) {
}
