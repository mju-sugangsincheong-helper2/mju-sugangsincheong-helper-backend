package com.mjusugangsincheonghelper.multigame.game.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.game.dto.GameApplyResponse;
import com.mjusugangsincheonghelper.multigame.game.dto.GameEnterResponse;
import com.mjusugangsincheonghelper.multigame.game.dto.GameWaitingResponse;
import com.mjusugangsincheonghelper.multigame.game.service.GameSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Tag(name = "Multigame", description = "실시간 멀티게임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame/session")
@PreAuthorize("hasRole('GUEST')")
@Validated
public class GameController {

	private final GameSessionService gameSessionService;

	@GetMapping(value = "/waiting-room", version = "1+")
	@Operation(summary = "Get waiting room", description = "현재 게임 상태를 조회하고 대기 상태에서 heartbeat를 갱신합니다.", responses = @ApiResponse(responseCode = "200", description = "조회 성공"))
	@OperationErrorCodes({ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<GameWaitingResponse>> waitingRoom() {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(gameSessionService.waitingRoom(memberId())));
	}

	@PostMapping(value = "/enter", version = "1+")
	@Operation(summary = "Enter game", description = "진행 중인 게임에 입장합니다.", responses = @ApiResponse(responseCode = "200", description = "입장 성공"))
	@OperationErrorCodes({ErrorCode.MULTIGAME_GAME_CANCELLED, ErrorCode.MULTIGAME_GAME_INVALID_STATE})
	public ResponseEntity<SingleSuccessResponseEnvelope<GameEnterResponse>> enter() {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(gameSessionService.enter(memberId())));
	}

	@PostMapping(value = "/leave", version = "1+")
	@Operation(summary = "Leave game", description = "게임과 대기열에서 이탈합니다.", responses = @ApiResponse(responseCode = "200", description = "이탈 성공"))
	@OperationErrorCodes({ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<Void>> leave() {
		gameSessionService.leave(memberId());
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.empty());
	}

	@PostMapping(value = "/apply", version = "1+")
	@Operation(summary = "Apply for a subject", description = "입장한 사용자의 과목 신청을 처리합니다.", responses = @ApiResponse(responseCode = "200", description = "신청 처리 성공"))
	@OperationErrorCodes({ErrorCode.GLOBAL_VALIDATION_ERROR, ErrorCode.MULTIGAME_GAME_INVALID_STATE, ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<GameApplyResponse>> apply(
			@Parameter(description = "신청할 과목 ID (1~6)", example = "1")
			@RequestParam @Min(1) @Max(6) int subjectId) {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(gameSessionService.apply(memberId(), subjectId)));
	}

	private long memberId() {
		return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
	}
}
