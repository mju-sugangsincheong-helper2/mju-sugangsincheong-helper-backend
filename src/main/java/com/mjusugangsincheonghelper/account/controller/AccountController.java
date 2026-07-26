package com.mjusugangsincheonghelper.account.controller;

import com.mjusugangsincheonghelper.account.dto.AccountDeviceResponse;
import com.mjusugangsincheonghelper.account.dto.AccountMeResponse;
import com.mjusugangsincheonghelper.account.service.AccountService;
import com.mjusugangsincheonghelper.auth.session.delivery.TokenDeliveryStrategy;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "회원 API")
@RestController
@PreAuthorize("hasRole('GUEST')")
@RequestMapping("/api/{version}/accounts")
@RequiredArgsConstructor
public class AccountController {

	private final AccountService accountService;
	private final TokenDeliveryStrategy tokenDeliveryStrategy;

	@GetMapping(value = "/me", version = "1+")
	@Operation(
			summary = "Account me",
			description = "내 정보 조회 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_SECURITY_UNAUTHORIZED_ACCESS,
			ErrorCode.AUTH_MEMBER_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<AccountMeResponse>> getMe() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Long memberId = (Long) authentication.getPrincipal();
		AccountMeResponse response = accountService.getMe(memberId);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/me/devices", version = "1+")
	@Operation(
			summary = "Account devices",
			description = "로그인된 기기 목록 조회 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_SECURITY_UNAUTHORIZED_ACCESS,
			ErrorCode.AUTH_MEMBER_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<List<AccountDeviceResponse>>> getMyDevices(
			HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Long memberId = (Long) authentication.getPrincipal();
		String currentRefreshToken = extractRefreshToken(request);

		List<AccountDeviceResponse> response = accountService.getDevices(memberId, currentRefreshToken);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@DeleteMapping(value = "/me", version = "1+")
	@Operation(
			summary = "Account withdraw",
			description = "회원 탈퇴 API. 모든 회원 정보와 디바이스 세션, 개인정보 동의 기록이 삭제되며 쿠키가 초기화됩니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "탈퇴 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_SECURITY_UNAUTHORIZED_ACCESS,
			ErrorCode.AUTH_MEMBER_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<Void>> withdraw(HttpServletResponse response) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Long memberId = (Long) authentication.getPrincipal();
		accountService.withdraw(memberId);
		tokenDeliveryStrategy.clear(response);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.empty());
	}

	private String extractRefreshToken(HttpServletRequest request) {
		String refreshTokenHeader = request.getHeader("X-Refresh-Token");
		if (refreshTokenHeader != null && !refreshTokenHeader.isBlank()) {
			return refreshTokenHeader;
		}
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
