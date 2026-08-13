package com.mjusugangsincheonghelper.account.controller;

import com.mjusugangsincheonghelper.account.dto.PrivacyAgreementResponse;
import com.mjusugangsincheonghelper.account.service.AccountAgreementService;
import com.mjusugangsincheonghelper.account.service.AccountAgreementService.ConsentStatus;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/{version}/auth/privacy")
@RequiredArgsConstructor
public class AccountAgreementController {

	private final AccountAgreementService accountAgreementService;
	private final SessionService sessionService;

	@PostMapping(value = "/agree", version = "1+")
	@PreAuthorize("hasRole('MEMBER')")
	@Operation(
			summary = "Privacy agreement",
			description = "현재 인증된 사용자의 개인정보 동의 감사 기록을 생성하거나 갱신합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "동의 기록 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_SECURITY_UNAUTHORIZED_ACCESS,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<PrivacyAgreementResponse>> agreePrivacyPolicy(
			HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Long memberId = (Long) authentication.getPrincipal();
		ConsentStatus status = accountAgreementService.agree(memberId);

		String refreshToken = extractRefreshToken(request);
		if (refreshToken != null) {
			// 동의 상태가 DB에 커밋됐으므로, 재발급되는 액세스 토큰의 agreed 클레임도 갱신된다
			sessionService.refreshSession(refreshToken, response);
		}

		PrivacyAgreementResponse res = PrivacyAgreementResponse.builder()
				.memberId(memberId)
				.privacyPolicyAgreed(status.status())
				.agreedAt(status.agreedAt())
				.build();
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(res));
	}

	private String extractRefreshToken(HttpServletRequest request) {
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if ("session_refresh_token".equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}
}
