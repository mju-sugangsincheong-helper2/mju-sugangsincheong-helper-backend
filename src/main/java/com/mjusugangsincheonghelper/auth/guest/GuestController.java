package com.mjusugangsincheonghelper.auth.guest;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.guest.dto.GuestCreateRequest;
import com.mjusugangsincheonghelper.auth.guest.dto.GuestResponse;
import com.mjusugangsincheonghelper.auth.session.SessionResult;
import com.mjusugangsincheonghelper.auth.session.SessionService;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/{version}/auth")
@RequiredArgsConstructor
public class GuestController {

	private final GuestService guestService;
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

		AuthenticatedIdentity identity = guestService.authenticate();
		SessionResult session = sessionService.createSession(identity, safeRequest.getDevice(), response);

		GuestResponse guestResponse = buildGuestResponse(session);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(SingleSuccessResponseEnvelope.of(guestResponse));
	}

	private GuestResponse buildGuestResponse(SessionResult session) {
		String accessToken = tokenInResponse ? session.getAccessToken() : null;
		String refreshToken = tokenInResponse ? session.getRefreshToken() : null;
		return GuestResponse.of(session.getMemberId(), session.getRole(), session.getName(), accessToken, refreshToken);
	}
}
