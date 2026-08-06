package com.mjusugangsincheonghelper.system.controller;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse;
import com.mjusugangsincheonghelper.system.service.SystemStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System", description = "시스템 설정 API")
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@RequestMapping("/api/{version}/system/stats")
public class SystemStatsController {

	private final SystemStatsService systemStatsService;

	@GetMapping(version = "1+")
	@Operation(
			summary = "System stats (domain metrics)",
			description = "관리자 모니터링용 도메인 지표 조회 (회원 구성/신규 가입/기기/공지/강좌). 인프라 지표는 Actuator/Prometheus에서 조회한다.",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<SystemStatsResponse>> getStats() {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(systemStatsService.getStats()));
	}
}