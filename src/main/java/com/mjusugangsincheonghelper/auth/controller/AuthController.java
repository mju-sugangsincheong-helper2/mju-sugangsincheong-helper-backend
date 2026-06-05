package com.mjusugangsincheonghelper.auth.controller;

import com.mjusugangsincheonghelper.auth.authentication.identity.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.authentication.merge.MergeService;
import com.mjusugangsincheonghelper.auth.authentication.guest.GuestAuthenticationProvider;
import com.mjusugangsincheonghelper.auth.dto.GuestCreateRequest;
import com.mjusugangsincheonghelper.auth.dto.GuestResponse;
import com.mjusugangsincheonghelper.auth.dto.LogoutRequest;
import com.mjusugangsincheonghelper.auth.dto.MergeRequest;
import com.mjusugangsincheonghelper.auth.dto.MergeResponse;
import com.mjusugangsincheonghelper.auth.dto.RefreshResponse;
import com.mjusugangsincheonghelper.auth.session.SessionResult;
import com.mjusugangsincheonghelper.auth.session.SessionService;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
public class AuthController {

	private final GuestAuthenticationProvider guestAuthenticationProvider;
	private final MergeService mergeService;
	private final SessionService sessionService;

	@Value("${app.auth.token-in-response:false}")
	private boolean tokenInResponse;

	@PostMapping(value = "/guest", version = "1+")
	@Operation(
			summary = "Guest create",
			description = "게스트 계정 생성 API",
			responses = {
					@ApiResponse(
							responseCode = "201",
							description = "게스트 생성 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<GuestResponse>> createGuest(
			@Valid @RequestBody(required = false) GuestCreateRequest request,
			HttpServletResponse response) {
		GuestCreateRequest safeRequest = request != null ? request : GuestCreateRequest.builder().build();

		AuthenticatedIdentity identity = guestAuthenticationProvider.authenticate();
		SessionResult session = sessionService.createSession(identity, safeRequest.getDevice(),
				safeRequest.getFcmToken(), response);

		GuestResponse guestResponse = buildGuestResponse(session);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(SingleSuccessResponseEnvelope.of(guestResponse));
	}

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

	@PostMapping(value = "/login/google/merge", version = "1+")
	@Operation(
			summary = "Guest data merge",
			description = "게스트 데이터를 Google 계정으로 병합하는 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "병합 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.AUTH_MERGE_TICKET_EXPIRED,
			ErrorCode.AUTH_MEMBER_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<MergeResponse>> merge(
			@Valid @RequestBody MergeRequest request,
			HttpServletResponse response) {
		AuthenticatedIdentity identity = mergeService.merge(request.getMergeTicket());
		SessionResult session = sessionService.createSession(identity, request.getDevice(),
				request.getFcmToken(), response);

		MergeResponse mergeResponse = buildMergeResponse(session);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(mergeResponse));
	}

	private GuestResponse buildGuestResponse(SessionResult session) {
		GuestResponse.GuestResponseBuilder builder = GuestResponse.builder()
				.memberId(session.getMemberId())
				.role(session.getRole())
				.name(session.getName());
		if (tokenInResponse) {
			builder.accessToken(session.getAccessToken())
					.refreshToken(session.getRefreshToken());
		}
		return builder.build();
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

	private MergeResponse buildMergeResponse(SessionResult session) {
		MergeResponse.MergeResponseBuilder builder = MergeResponse.builder()
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
