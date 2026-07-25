package com.mjusugangsincheonghelper.auth.oauth;

import com.mjusugangsincheonghelper.auth.oauth.dto.OAuthConfigResponse;
import com.mjusugangsincheonghelper.auth.oauth.dto.OAuthStartResponse;
import com.mjusugangsincheonghelper.auth.oauth.dto.OAuthTokenRequest;
import com.mjusugangsincheonghelper.auth.oauth.dto.OAuthTokenResponse;
import com.mjusugangsincheonghelper.auth.session.SessionResult;
import com.mjusugangsincheonghelper.auth.session.SessionService;
import com.mjusugangsincheonghelper.auth.session.token.TokenProvider;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "OAuth", description = "Google OAuth 인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/auth")
public class GoogleOAuthController {

	private final OAuthStateService oAuthStateService;
	private final GoogleOAuthService googleOAuthService;
	private final SessionService sessionService;
	private final TokenProvider tokenProvider;

	@Value("${app.auth.token-in-response:false}")
	private boolean tokenInResponse;

	@Value("${app.oauth2.google.client-id}")
	private String clientId;

	@Value("${app.oauth2.google.redirect-uri}")
	private String redirectUri;

	@GetMapping(value = "/config/google", version = "1+")
	@Operation(
			summary = "Google OAuth config",
			description = "Google 로그인에 필요한 설정 정보를 반환합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	public ResponseEntity<SingleSuccessResponseEnvelope<OAuthConfigResponse>> getGoogleConfig() {
		OAuthConfigResponse response = OAuthConfigResponse.builder()
				.clientId(clientId)
				.scopes(List.of("openid", "profile", "email"))
				.redirectUri(redirectUri)
				.build();
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@PostMapping(value = "/oauth/start", version = "1+")
	@Operation(
			summary = "OAuth 시작",
			description = "Google 인증 URL을 생성하여 반환합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "인증 URL 생성 성공"
					)
			}
	)
	public ResponseEntity<SingleSuccessResponseEnvelope<OAuthStartResponse>> oauthStart() {
		String state = oAuthStateService.createState();

		String googleAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth"
				+ "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
				+ "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
				+ "&response_type=code"
				+ "&scope=" + URLEncoder.encode("openid profile email", StandardCharsets.UTF_8)
				+ "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
				+ "&hd=mju.ac.kr"
				+ "&access_type=offline";

		OAuthStartResponse response = OAuthStartResponse.builder()
				.googleAuthUrl(googleAuthUrl)
				.build();
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@PostMapping(value = "/token", version = "1+")
	@Operation(
			summary = "토큰 교환",
			description = "Google authorization code를 전달받아 Google 인증을 처리하고 JWT를 발급합니다. "
					+ "게스트 상태에서 기존 Google 계정으로 로그인 시 409(AUTH_005)와 mergeTicket을 반환합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "토큰 교환 성공"
					),
					@ApiResponse(
							responseCode = "409",
							description = "게스트 데이터 병합 필요 (mergeTicket 포함)"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.AUTH_GOOGLE_AUTH_FAILED,
			ErrorCode.AUTH_NOT_MJU_DOMAIN,
			ErrorCode.AUTH_MEMBER_NOT_FOUND,
			ErrorCode.AUTH_MERGE_REQUIRED,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<OAuthTokenResponse>> tokenExchange(
			@Valid @RequestBody OAuthTokenRequest request,
			HttpServletResponse httpResponse) {
		if (!oAuthStateService.consumeState(request.getState())) {
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED);
		}

		Long guestMemberId = extractGuestMemberId(request.getAccessToken());

		OAuthAuthenticationResult authResult = googleOAuthService.authenticate(request.getCode(), guestMemberId);

		if (authResult.isMergeRequired()) {
			OAuthTokenResponse response = OAuthTokenResponse.builder()
					.status("MERGE_REQUIRED")
					.newUser(false)
					.mergeTicket(authResult.getMergeTicket())
					.build();
			return ResponseEntity
					.status(HttpStatus.CONFLICT)
					.body(SingleSuccessResponseEnvelope.of(response));
		}

		SessionResult session = sessionService.createSession(authResult.getIdentity(), null, null, httpResponse);
		OAuthTokenResponse response = buildOAuthTokenResponse(session, authResult.isNewUser());

		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	private Long extractGuestMemberId(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			return null;
		}
		try {
			TokenProvider.TokenClaims claims = tokenProvider.parseAccessToken(accessToken);
			if ("GUEST".equals(claims.role())) {
				return claims.memberId();
			}
		} catch (Exception e) {
			log.debug("Invalid access token for guest detection: {}", e.getMessage());
		}
		return null;
	}

	private OAuthTokenResponse buildOAuthTokenResponse(SessionResult session, boolean newUser) {
		OAuthTokenResponse.OAuthTokenResponseBuilder builder = OAuthTokenResponse.builder()
				.status("SUCCESS")
				.newUser(newUser)
				.memberId(session.getMemberId())
				.role(session.getRole())
				.name(session.getName())
				.position(session.getPosition())
				.department(session.getDepartment());

		if (tokenInResponse) {
			builder.accessToken(session.getAccessToken())
					.refreshToken(session.getRefreshToken());
		}

		return builder.build();
	}
}
