package com.mjusugangsincheonghelper.multigame.stats.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.stats.dto.DepartmentParticipationStatsResponse;
import com.mjusugangsincheonghelper.multigame.stats.dto.DepartmentSuccessRateStatsResponse;
import com.mjusugangsincheonghelper.multigame.stats.service.MultigameDepartmentStatsService;
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
public class MultigameStatsController {

	private final MultigameDepartmentStatsService departmentStatsService;

	@GetMapping(value = "/stats/department/participation", version = "1+")
	@Operation(
			summary = "Get department participation ranking",
			description = "학과별 참여 횟수 순위 조회 API (상위 10개 + 내 학과)",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<DepartmentParticipationStatsResponse>> getDepartmentParticipationStats() {
		Long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		DepartmentParticipationStatsResponse stats = departmentStatsService.getParticipationStats(memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(stats));
	}

	@GetMapping(value = "/stats/department/success-rate", version = "1+")
	@Operation(
			summary = "Get department success rate ranking",
			description = "학과별 성공률 순위 조회 API (상위 10개 + 내 학과)",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<DepartmentSuccessRateStatsResponse>> getDepartmentSuccessRateStats() {
		Long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		DepartmentSuccessRateStatsResponse stats = departmentStatsService.getSuccessRateStats(memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(stats));
	}
}
