package com.mjusugangsincheonghelper.multigame.dashboard.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.dashboard.dto.DashboardResponse;
import com.mjusugangsincheonghelper.multigame.dashboard.service.MultigameDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Multigame", description = "멀티게임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame")
@PreAuthorize("hasRole('GUEST')")
public class MultigameDashboardController {

	private final MultigameDashboardService dashboardService;

	@GetMapping(value = "/dashboard", version = "1+")
	@Operation(
			summary = "Get dashboard",
			description = "멀티게임 대시보드 조회 API (오늘의 게임, 내 최근 기록, 전체 통계)",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<DashboardResponse>> getDashboard() {
		Long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		DashboardResponse dashboard = dashboardService.getDashboard(memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(dashboard));
	}
}
