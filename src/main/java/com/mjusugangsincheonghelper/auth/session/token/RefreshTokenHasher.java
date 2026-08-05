package com.mjusugangsincheonghelper.auth.session.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * refresh token(UUID)의 단방향 SHA-256 해시. DB에는 원문 대신 이 해시만 저장한다.
 */
public final class RefreshTokenHasher {

	private RefreshTokenHasher() {
	}

	public static String hash(String refreshToken) {
		if (refreshToken == null) {
			return null;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}
}
