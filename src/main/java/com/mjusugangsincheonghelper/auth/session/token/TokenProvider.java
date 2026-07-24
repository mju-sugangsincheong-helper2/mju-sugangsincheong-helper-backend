package com.mjusugangsincheonghelper.auth.session.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenProvider {

	private final SecretKey key;
	private final long accessTokenExpiryMs;
	private final long refreshTokenExpiryMs;
	private final long mergeTicketExpiryMs;

	public TokenProvider(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.access-token-expiry-ms:3600000}") long accessTokenExpiryMs,
			@Value("${app.jwt.refresh-token-expiry-ms:604800000}") long refreshTokenExpiryMs,
			@Value("${app.jwt.merge-ticket-expiry-ms:300000}") long mergeTicketExpiryMs) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpiryMs = accessTokenExpiryMs;
		this.refreshTokenExpiryMs = refreshTokenExpiryMs;
		this.mergeTicketExpiryMs = mergeTicketExpiryMs;
	}

	public String createAccessToken(Long memberId, String role, boolean privacyAgreed) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim("role", role)
				.claim("agreed", privacyAgreed)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(accessTokenExpiryMs)))
				.signWith(key)
				.compact();
	}

	public String createRefreshToken() {
		return UUID.randomUUID().toString();
	}

	public String createMergeTicket(Long memberId, String googleSubId) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim("googleSubId", googleSubId)
				.claim("type", "merge")
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(mergeTicketExpiryMs)))
				.signWith(key)
				.compact();
	}

	public TokenClaims parseAccessToken(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
		return new TokenClaims(
				Long.parseLong(claims.getSubject()),
				claims.get("role", String.class),
				claims.get("agreed", Boolean.class) != null && claims.get("agreed", Boolean.class)
		);
	}

	public Claims parseMergeTicket(String ticket) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(ticket)
				.getPayload();
	}

	public long getAccessTokenExpiryMs() {
		return accessTokenExpiryMs;
	}

	public long getRefreshTokenExpiryMs() {
		return refreshTokenExpiryMs;
	}

	public record TokenClaims(Long memberId, String role, boolean agreed) {
	}
}
