package com.mjusugangsincheonghelper.multigame.session.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.common.GameTimeCalculator;
import com.mjusugangsincheonghelper.multigame.session.dto.GameRequestResponse;
import com.mjusugangsincheonghelper.multigame.session.dto.WaitingRoomResponse;
import com.mjusugangsincheonghelper.multigame.session.service.MultigameSessionService;
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

@Tag(name = "Multigame", description = "멀티게임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame")
@PreAuthorize("hasRole('GUEST')")
public class MultigameSessionController {

	private final MultigameSessionService sessionService;

	@GetMapping(value = "/session/waiting-room", version = "1+")
	@Operation(
			summary = "Enter waiting room",
			description = "대기방 입장 및 상태 폴링 API (3초 간격 폴링)",
			responses = {
					@ApiResponse(responseCode = "200", description = "대기방 정보 조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.MULTIGAME_GAME_CANCELLED,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<WaitingRoomResponse>> enterWaitingRoom() {
		Long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String t = GameTimeCalculator.computeActiveGameT(java.time.LocalDateTime.now());
		WaitingRoomResponse response = sessionService.enterWaitingRoom(t, memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@PostMapping(value = "/session/request", version = "1+")
	@Operation(
			summary = "Game request",
			description = "게임 신청 API (폴링 방식으로 대기 후 자동 처리)",
			responses = {
					@ApiResponse(responseCode = "200", description = "요청 처리 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.MULTIGAME_GAME_CANCELLED,
			ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<GameRequestResponse>> requestGame(
			@Parameter(description = "신청 과목 ID (1~6)", example = "1")
			@RequestParam("subjectId") @Min(1) @Max(6) int subjectId) {
		Long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String t = GameTimeCalculator.computeActiveGameT(java.time.LocalDateTime.now());
		GameRequestResponse response = sessionService.requestGame(t, memberId, subjectId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}
}
