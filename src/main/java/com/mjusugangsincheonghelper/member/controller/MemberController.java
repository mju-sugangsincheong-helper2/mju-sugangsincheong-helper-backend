package com.mjusugangsincheonghelper.member.controller;

import com.mjusugangsincheonghelper.member.dto.MemberMeResponse;
import com.mjusugangsincheonghelper.member.service.MemberService;
import com.mjusugangsincheonghelper.auth.session.delivery.TokenDeliveryStrategy;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member", description = "회원 API")
@RestController
@RequestMapping("/api/{version}/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;
	private final TokenDeliveryStrategy tokenDeliveryStrategy;

	@GetMapping(value = "/me", version = "1+")
	@Operation(
			summary = "Member me",
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
	public ResponseEntity<SingleSuccessResponseEnvelope<MemberMeResponse>> getMe() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Long memberId = (Long) authentication.getPrincipal();
		MemberMeResponse response = memberService.getMe(memberId);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@DeleteMapping(value = "/me", version = "1+")
	@Operation(
			summary = "Member withdraw",
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
		memberService.withdraw(memberId);
		tokenDeliveryStrategy.clear(response);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.empty());
	}
}
