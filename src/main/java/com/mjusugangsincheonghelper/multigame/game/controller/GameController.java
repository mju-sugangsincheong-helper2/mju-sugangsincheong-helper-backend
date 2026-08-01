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
	@Operation(summary = "Get waiting room", description = """
			현재 시각 기준 타겟 게임(T)의 대기방 상태를 조회합니다. 응답으로 게임 식별자
			multigameId(T, yyyyMMddHHmmss 14자리)와 최종 판정 상태 state(WAITING/READY/STARTING/
			PROGRESS/ENDED/CLOSED/CANCELLED), 참여 인원 participation이 반환됩니다.
			WAITING 또는 READY 상태일 때만 서버가 사용자의 heartbeat를 갱신해 대기 인원으로
			집계하므로, 클라이언트는 1초 간격으로 폴링하며 PROGRESS 상태에서 진입 API
			(POST /enter)를 호출할 수 있습니다. 새벽 2시~5시에는 CLOSED가 반환됩니다.
			비즈니스 예외는 발생하지 않으며 서버 오류 시에만 500(GLOBAL_004)이 반환됩니다.
			""", responses = @ApiResponse(responseCode = "200", description = "대기방 상태 — multigameId(T), state(WAITING/READY/STARTING/PROGRESS/ENDED/CLOSED/CANCELLED), participation(대기/진행 참여 인원)"))
	@OperationErrorCodes({ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<GameWaitingResponse>> waitingRoom() {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(gameSessionService.waitingRoom(memberId())));
	}

	@PostMapping(value = "/enter", version = "1+")
	@Operation(summary = "Enter game", description = """
			진행 중인 게임(PROGRESS)에 입장하여 메인 방 참여자(P)로 마킹합니다. 응답으로 게임 식별자
			multigameId(T), 현재 상태 state(PROGRESS), 입장 후 참여 인원 participation을 반환합니다.
			호출 전 대기방 API(GET /waiting-room)에서 PROGRESS 상태를 확인해야 하며, 게임이 진행
			중이 아니면 409(MULTIGAME_002), 최소 인원 미달로 취소된 경우 410(MULTIGAME_003)을
			반환합니다. 본 API를 호출하지 않으면 신청 API(POST /apply)가 거부되므로, 과목 신청 전
			반드시 먼저 호출해야 합니다.
			""", responses = @ApiResponse(responseCode = "200", description = "진입 성공 — multigameId(T), state(PROGRESS), participation(입장 후 참여 인원)"))
	@OperationErrorCodes({ErrorCode.MULTIGAME_GAME_CANCELLED, ErrorCode.MULTIGAME_GAME_INVALID_STATE})
	public ResponseEntity<SingleSuccessResponseEnvelope<GameEnterResponse>> enter() {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(gameSessionService.enter(memberId())));
	}

	@PostMapping(value = "/leave", version = "1+")
	@Operation(summary = "Leave game", description = """
			게임 참여자 마킹(P)과 신청 대기열(queue)에서 현재 사용자를 제거합니다. 대기열에 대기
			중이면 함께 제거되어 불필요한 대기/공급을 방지합니다. 모든 상태(CLOSED/WAITING/READY/
			PROGRESS/ENDED/CANCELLED)에서 200으로 응답하며, 게임이 진행 중이 아닐 때는 아무 동작도
			하지 않습니다(no-op). 응답 본문에는 data가 없는 빈 응답(meta만 포함)이 반환됩니다.
			비즈니스 예외는 발생하지 않으며 서버 오류 시에만 500(GLOBAL_004)이 반환됩니다.
			""", responses = @ApiResponse(responseCode = "200", description = "이탈 처리 완료 — data 없는 빈 응답"))
	@OperationErrorCodes({ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<Void>> leave() {
		gameSessionService.leave(memberId());
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.empty());
	}

	@PostMapping(value = "/apply", version = "1+")
	@Operation(summary = "Apply for a subject", description = """
			메인 방에 입장한 사용자의 과목 신청을 처리합니다. 응답의 status는 BLOCKED(신청 불가 상태),
			PENDING(대기열 순번 seq > 입장 허용선 limit, 재시도 필요), SUCCESS(신청 성공, 남은 좌석
			remaining), FAIL_SOLDOUT(정원 초과), FAIL_DUPLICATE(이미 신청 완료) 중 하나이며,
			상태별로 관련 필드(seq/limit/subjectId/remaining)가 채워져 반환됩니다. 게임 진행 시간은
			30초(T ~ T+30s)이며, 클라이언트는 성공할 때까지 동일 요청을 반복(폴링)합니다. 호출 전
			반드시 진입 API(POST /enter)를 호출해야 하며, 미진입 상태의 신청은 409(MULTIGAME_002)로
			거부됩니다. 과목 ID가 1~6 범위를 벗어나면 400(GLOBAL_002), Lua 실행 실패 시
			500(MULTIGAME_005)이 반환되고, 최종 결과는 결과 조회 API(GET /results)에서 확인할 수
			있습니다.
			""", responses = @ApiResponse(responseCode = "200", description = "신청 처리 결과 — status(BLOCKED/PENDING/SUCCESS/FAIL_SOLDOUT/FAIL_DUPLICATE) 및 상태별 상세 필드(seq, limit, subjectId, remaining)"))
	@OperationErrorCodes({ErrorCode.GLOBAL_VALIDATION_ERROR, ErrorCode.MULTIGAME_GAME_INVALID_STATE, ErrorCode.MULTIGAME_LUA_SCRIPT_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<GameApplyResponse>> apply(
			@Parameter(description = "신청할 과목 ID — 1~6 사이의 정수 (필수, 기본값 없음)", example = "1")
			@RequestParam(name = "subjectId") @Min(1) @Max(6) int subjectId) {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(gameSessionService.apply(memberId(), subjectId)));
	}

	private long memberId() {
		return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
	}
}
