package com.mjusugangsincheonghelper.auth.merge;

import com.mjusugangsincheonghelper.account.service.AccountAgreementService;
import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.merge.dto.MergeRequest;
import com.mjusugangsincheonghelper.auth.merge.dto.MergeResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/{version}/auth")
@RequiredArgsConstructor
public class MergeController {

	private final MergeService mergeService;
	private final SessionService sessionService;
	private final AccountAgreementService accountAgreementService;

	@Value("${app.auth.token-in-response:false}")
	private boolean tokenInResponse;

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
			ErrorCode.AUTH_GUEST_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<MergeResponse>> merge(
			@Valid @RequestBody MergeRequest request,
			HttpServletResponse response) {
		AuthenticatedIdentity identity = mergeService.merge(request.getMergeTicket());
		SessionResult session = sessionService.createSession(identity, request.getDevice(), response);

		MergeResponse mergeResponse = buildMergeResponse(session);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(mergeResponse));
	}

	private MergeResponse buildMergeResponse(SessionResult session) {
		MergeResponse.MergeResponseBuilder builder = MergeResponse.builder()
				.memberId(session.getMemberId())
				.role(session.getRole())
				.name(session.getName())
				.position(session.getPosition())
				.department(session.getDepartment())
				.newUser(!accountAgreementService.isAgreed(session.getMemberId()));
		if (tokenInResponse) {
			builder.accessToken(session.getAccessToken())
					.refreshToken(session.getRefreshToken());
		}
		return builder.build();
	}
}
