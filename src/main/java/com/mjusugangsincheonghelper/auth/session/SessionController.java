package com.mjusugangsincheonghelper.auth.session;

import com.mjusugangsincheonghelper.auth.session.dto.LogoutRequest;
import com.mjusugangsincheonghelper.auth.session.dto.RefreshResponse;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/{version}/auth")
@RequiredArgsConstructor
public class SessionController {

	private final SessionService sessionService;

	@Value("${app.auth.token-in-response:false}")
	private boolean tokenInResponse;

	@PostMapping(value = "/refresh", version = "1+")
	@Operation(
			summary = "Token refresh",
			description = "토큰 재발급 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "토큰 재발급 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.AUTH_INVALID_REFRESH_TOKEN,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<RefreshResponse>> refreshToken(
			HttpServletRequest request,
			HttpServletResponse response) {
		String refreshToken = extractRefreshToken(request);
		SessionResult session = sessionService.refreshSession(refreshToken, response);

		RefreshResponse refreshResponse = buildRefreshResponse(session);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(refreshResponse));
	}

	@PostMapping(value = "/logout", version = "1+")
	@Operation(
			summary = "Logout",
			description = "로그아웃 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "로그아웃 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_SECURITY_UNAUTHORIZED_ACCESS,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<Void>> logout(
			@RequestBody(required = false) LogoutRequest request,
			HttpServletRequest request_,
			HttpServletResponse response) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Long memberId = (Long) authentication.getPrincipal();
		String refreshToken = extractRefreshToken(request_);
		String fcmToken = request != null ? request.getFcmToken() : null;

		sessionService.destroySession(refreshToken, fcmToken, memberId, response);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.empty());
	}

	private RefreshResponse buildRefreshResponse(SessionResult session) {
		RefreshResponse.RefreshResponseBuilder builder = RefreshResponse.builder()
				.status("success")
				.role(session.getRole());
		if (tokenInResponse) {
			builder.accessToken(session.getAccessToken())
					.refreshToken(session.getRefreshToken());
		}
		return builder.build();
	}

	private String extractRefreshToken(HttpServletRequest request) {
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if ("refresh_token".equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}
}
