package com.mjusugangsincheonghelper.multigame.session.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.session.service.DevGameInitializer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Multigame", description = "멀티게임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame/lifecycle")
@Profile("dev")
@PreAuthorize("hasRole('ADMIN')")
public class MultigameLifeCycleControlController {

	private final DevGameInitializer devGameInitializer;

	@GetMapping(value = "/state/{multigameId}", version = "1+")
	@Operation(
			summary = "Get game state",
			description = "게임 상태 조회 API (dev 전용)",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<String>> getState(
			@Parameter(description = "멀티게임 ID", example = "20260726100000")
			@PathVariable("multigameId") String multigameId) {
		String state = devGameInitializer.getState(multigameId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(state));
	}

	@PostMapping(value = "/transition/{multigameId}", version = "1+")
	@Operation(
			summary = "Transition game state",
			description = "게임 상태 수동 전이 API (dev 전용). WAITING → READY → PROGRESS → ENDED → FINALIZE",
			responses = {
					@ApiResponse(responseCode = "200", description = "전이 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<String>> transitionState(
			@Parameter(description = "멀티게임 ID", example = "20260726100000")
			@PathVariable("multigameId") String multigameId,
			@Parameter(description = "목표 상태 (WAITING, READY, PROGRESS, ENDED, FINALIZE, CANCELLED)", example = "READY")
			@RequestParam("targetState") String targetState) {
		devGameInitializer.transitionState(multigameId, targetState);
		String newState = devGameInitializer.getState(multigameId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(newState));
	}
}
