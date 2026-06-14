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
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import com.mjusugangsincheonghelper.system.definition.SettingDefinition;

@Component
public class TokenProvider {

	private final SecretKey key;
	private final SystemConfigService systemConfigService;

	public TokenProvider(
			@Value("${app.jwt.secret}") String secret,
			SystemConfigService systemConfigService) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.systemConfigService = systemConfigService;
	}

	public String createAccessToken(Long memberId, String role) {
		Instant now = Instant.now();
		SettingDefinition.JwtExpiryConfig config = SettingDefinition.JWT_EXPIRY_CONFIG.getFrom(systemConfigService);
		long accessTokenExpiryMs = config.accessTokenExpiryMs();
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim("role", role)
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
		SettingDefinition.JwtExpiryConfig config = SettingDefinition.JWT_EXPIRY_CONFIG.getFrom(systemConfigService);
		long mergeTicketExpiryMs = config.mergeTicketExpiryMs();
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
				claims.get("role", String.class)
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
		SettingDefinition.JwtExpiryConfig config = SettingDefinition.JWT_EXPIRY_CONFIG.getFrom(systemConfigService);
		return config.accessTokenExpiryMs();
	}

	public long getRefreshTokenExpiryMs() {
		SettingDefinition.JwtExpiryConfig config = SettingDefinition.JWT_EXPIRY_CONFIG.getFrom(systemConfigService);
		return config.refreshTokenExpiryMs();
	}

	public record TokenClaims(Long memberId, String role) {
	}
}
