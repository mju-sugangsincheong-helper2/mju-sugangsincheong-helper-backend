package com.mjusugangsincheonghelper.auth.oauth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.merge.MergeTicketService;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoogleOAuthService {

	private static final String MJU_DOMAIN = "mju.ac.kr";
	private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
	private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final MergeTicketService mergeTicketService;
	private final JsonMapper jsonMapper;

	@Value("${app.oauth2.google.client-id}")
	private String clientId;

	@Value("${app.oauth2.google.client-secret}")
	private String clientSecret;

	@Value("${app.oauth2.google.redirect-uri}")
	private String redirectUri;

	private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
	private volatile long keyCacheExpiresAt = 0;

	@Transactional
	public OAuthAuthenticationResult authenticate(String code, Long guestMemberId) {
		String idToken = exchangeCodeForIdToken(code);
		Claims claims = verifyAndParseIdToken(idToken);

		validateMjuDomain(claims);

		String googleSubId = claims.getSubject();
		ParsedName parsedName = parseName(claims.get("name", String.class));

		return authenticateOrCreateMember(googleSubId, parsedName, guestMemberId);
	}

	private OAuthAuthenticationResult authenticateOrCreateMember(String googleSubId, ParsedName parsedName, Long guestMemberId) {
		Optional<MemberAuth> existingAuth = memberAuthRepository.findByAuthKeyAndAuthType(googleSubId, AuthType.GOOGLE);
		if (existingAuth.isPresent()) {
			var member = memberRepository.findById(existingAuth.get().getMemberId())
					.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
			existingAuth.get().updateLastLoginAt();

			member.promoteToMember(parsedName.name(), parsedName.position(), parsedName.department());
			memberRepository.save(member);

			if (guestMemberId != null) {
				String mergeTicket = mergeTicketService.createTicket(guestMemberId, googleSubId);
				return OAuthAuthenticationResult.mergeRequired(mergeTicket, googleSubId);
			}

			return OAuthAuthenticationResult.success(
					AuthenticatedIdentity.builder().memberId(member.getId()).build(), false);
		}

		Member member = Member.builder()
				.role(Role.MEMBER)
				.name(parsedName.name())
				.position(parsedName.position())
				.department(parsedName.department())
				.build();
		member = memberRepository.save(member);

		MemberAuth memberAuth = MemberAuth.builder()
				.memberId(member.getId())
				.authType(AuthType.GOOGLE)
				.authKey(googleSubId)
				.build();
		memberAuth.updateLastLoginAt();
		memberAuthRepository.save(memberAuth);

		return OAuthAuthenticationResult.success(
				AuthenticatedIdentity.builder().memberId(member.getId()).build(), true);
	}

	private String exchangeCodeForIdToken(String code) {
		RestClient restClient = RestClient.create();

		String body = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
				+ "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
				+ "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
				+ "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
				+ "&grant_type=authorization_code";

		String response = restClient.post()
				.uri(GOOGLE_TOKEN_URI)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.body(body)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED);
				})
				.body(String.class);

		try {
			JsonNode json = jsonMapper.readTree(response);
			return json.get("id_token").asString();
		} catch (Exception e) {
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED, e);
		}
	}

	private Claims verifyAndParseIdToken(String idToken) {
		try {
			return Jwts.parser()
					.keyLocator(header -> {
						String kid = (String) header.get("kid");
						return getPublicKey(kid);
					})
					.build()
					.parseSignedClaims(idToken)
					.getPayload();
		} catch (BaseException e) {
			throw e;
		} catch (Exception e) {
			log.warn("ID token verification failed: {}", e.getMessage());
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED, e);
		}
	}

	private PublicKey getPublicKey(String kid) {
		refreshKeyCacheIfNeeded();
		PublicKey key = keyCache.get(kid);
		if (key == null) {
			keyCache.clear();
			refreshKeyCacheIfNeeded();
			key = keyCache.get(kid);
		}
		if (key == null) {
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED);
		}
		return key;
	}

	private void refreshKeyCacheIfNeeded() {
		if (System.currentTimeMillis() < keyCacheExpiresAt && !keyCache.isEmpty()) {
			return;
		}
		try {
			String jwksResponse = RestClient.create()
					.get().uri(GOOGLE_JWKS_URI)
					.retrieve().body(String.class);

			JsonNode jwks = jsonMapper.readTree(jwksResponse);
			Map<String, PublicKey> newKeys = new ConcurrentHashMap<>();

			for (JsonNode keyNode : jwks.get("keys")) {
				String kid = keyNode.get("kid").asString();
				byte[] nBytes = Base64.getUrlDecoder().decode(keyNode.get("n").asString());
				byte[] eBytes = Base64.getUrlDecoder().decode(keyNode.get("e").asString());

				BigInteger modulus = new BigInteger(1, nBytes);
				BigInteger exponent = new BigInteger(1, eBytes);

				PublicKey publicKey = KeyFactory.getInstance("RSA")
						.generatePublic(new RSAPublicKeySpec(modulus, exponent));
				newKeys.put(kid, publicKey);
			}

			keyCache.clear();
			keyCache.putAll(newKeys);
			keyCacheExpiresAt = System.currentTimeMillis() + 3600_000;
		} catch (Exception e) {
			log.error("Failed to fetch Google JWKS", e);
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED, e);
		}
	}

	private void validateMjuDomain(Claims claims) {
		String hd = claims.get("hd", String.class);
		if (hd == null || !MJU_DOMAIN.equals(hd)) {
			log.warn("Non-MJU domain attempted login: hd={}", hd);
			throw new BaseException(ErrorCode.AUTH_NOT_MJU_DOMAIN);
		}
	}

	private ParsedName parseName(String rawName) {
		if (rawName == null || rawName.isBlank()) {
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED);
		}
		String[] parts = rawName.split("/");
		if (parts.length < 3) {
			log.warn("Invalid name format: expected 'name/position/department', got '{}'", rawName);
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED);
		}
		String name = parts[0].trim();
		String position = parts[1].trim();
		String department = parts[2].trim();
		if (name.isBlank() || position.isBlank() || department.isBlank()) {
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED);
		}
		return new ParsedName(name, position, department);
	}

	private record ParsedName(String name, String position, String department) {}
}
