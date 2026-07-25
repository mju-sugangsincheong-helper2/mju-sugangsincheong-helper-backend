package com.mjusugangsincheonghelper.multigame.result.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameResultDetailResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameResultResponse;
import com.mjusugangsincheonghelper.multigame.result.service.MultigameResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Multigame", description = "멀티게임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame")
@PreAuthorize("hasRole('GUEST')")
public class MultigameResultController {

	private final MultigameResultService resultService;

	@GetMapping(value = "/results/{multigameId}", version = "1+")
	@Operation(
			summary = "Get game result",
			description = "게임 결과 조회 API (게임 메타 + 유저별 결과)",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.MULTIGAME_RESULT_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<MultigameResultResponse>> getGameResult(
			@Parameter(description = "멀티게임 ID (14자리)", example = "20260630120000")
			@PathVariable String multigameId) {
		MultigameResultResponse response = resultService.getGameResult(multigameId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/results/my", version = "1+")
	@Operation(
			summary = "Get my result",
			description = "내 게임 결과 조회 API",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.MULTIGAME_RESULT_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<MultigameResultDetailResponse>> getMyResult(
			@Parameter(description = "멀티게임 ID (14자리)", example = "20260630120000")
			@RequestParam String multigameId) {
		Long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		MultigameResultDetailResponse response = resultService.getMyResult(multigameId, memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}
}
