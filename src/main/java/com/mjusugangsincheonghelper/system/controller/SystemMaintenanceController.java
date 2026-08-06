package com.mjusugangsincheonghelper.system.controller;

import com.mjusugangsincheonghelper.auth.session.device.DeviceSessionService;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.system.dto.DeviceCleanupResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System", description = "시스템 설정 API")
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@RequestMapping("/api/{version}/system")
public class SystemMaintenanceController {

	private final DeviceSessionService deviceSessionService;

	@PostMapping(path = "/devices/cleanup", version = "1+")
	@Operation(
			summary = "Clean expired device sessions / FCM tokens",
			description = "만료된 기기 세션(접속 세션 만료 시각이 지난 기기, 바인딩된 FCM 토큰 포함)을 일괄 삭제하고 삭제 개수를 반환한다.",
			responses = {
					@ApiResponse(responseCode = "200", description = "정리 완료")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<DeviceCleanupResult>> cleanupExpiredDevices() {
		long cleared = deviceSessionService.deleteExpired();
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(new DeviceCleanupResult(cleared)));
	}
}