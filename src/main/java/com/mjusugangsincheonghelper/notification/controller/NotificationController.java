package com.mjusugangsincheonghelper.notification.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenDeleteRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenRegisterRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenResponse;
import com.mjusugangsincheonghelper.notification.dto.NotificationTestRequest;
import com.mjusugangsincheonghelper.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 및 FCM 토큰 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/notification")
public class NotificationController {

	private final NotificationService notificationService;

	@PreAuthorize("hasRole('GUEST')")
	@PostMapping(value = "/token", version = "1+")
	@Operation(
			summary = "Register or Update FCM Token",
			description = "현재 사용자의 FCM 토큰을 등록하거나 갱신합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "FCM 토큰 등록/갱신 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_SECURITY_UNAUTHORIZED_ACCESS,
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<NotificationTokenResponse>> registerToken(
			HttpServletRequest httpRequest,
			@Valid @RequestBody NotificationTokenRegisterRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Long memberId = (Long) authentication.getPrincipal();
		Long deviceId = (Long) httpRequest.getAttribute("deviceId");
		NotificationTokenResponse response = notificationService.registerToken(memberId, deviceId, request);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@PreAuthorize("hasRole('GUEST')")
	@DeleteMapping(value = "/token", version = "1+")
	@Operation(
			summary = "Delete FCM Token",
			description = "현재 사용자의 기기에 등록된 FCM 토큰을 삭제/제거합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "FCM 토큰 삭제 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_SECURITY_UNAUTHORIZED_ACCESS,
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<Void>> deleteToken(
			HttpServletRequest httpRequest,
			@Valid @RequestBody NotificationTokenDeleteRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Long memberId = (Long) authentication.getPrincipal();
		Long deviceId = (Long) httpRequest.getAttribute("deviceId");
		notificationService.deleteToken(memberId, deviceId, request);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.empty());
	}

	@PreAuthorize("hasRole('GUEST')")
	@PostMapping(value = "/test", version = "1+")
	@Operation(
			summary = "Send Test Notification",
			description = "현재 기기로 테스트 알림을 발송합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "테스트 알림 발송 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_SECURITY_UNAUTHORIZED_ACCESS,
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.NOTIFICATION_TOKEN_NOT_FOUND,
			ErrorCode.NOTIFICATION_SEND_FAILED,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<Void>> sendTestNotification(
			HttpServletRequest httpRequest,
			@Valid @RequestBody NotificationTestRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Long memberId = (Long) authentication.getPrincipal();
		Long deviceId = (Long) httpRequest.getAttribute("deviceId");
		notificationService.sendTestNotification(memberId, deviceId, request);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.empty());
	}
}
