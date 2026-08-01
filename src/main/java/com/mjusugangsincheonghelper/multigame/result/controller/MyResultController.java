package com.mjusugangsincheonghelper.multigame.result.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.PagedSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundLogResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundRecordResponse;
import com.mjusugangsincheonghelper.multigame.result.service.RoundResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Multigame", description = "멀티게임 내 결과 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame/me/results")
@PreAuthorize("hasRole('GUEST')")
@Validated
public class MyResultController {

	private final RoundResultService roundResultService;

	@GetMapping(version = "1+")
	@Operation(summary = "Get my round results", description = """
			현재 로그인한 사용자가 참여한 라운드들의 최종 결과를 최신순으로 페이징 조회합니다.
			각 행은 최종 상태(status: SUCCESS/FAIL_SOLDOUT)와 신청 과목(subjectId)을 포함하며,
			결과 화면의 카드 데이터로 사용됩니다. 신청 시도의 상세 타임라인은 로그 API
			(GET /me/results/{multigameId}/log)에서 확인할 수 있습니다.
			""", responses = @ApiResponse(responseCode = "200", description = "내 참여 이력 목록 (최신순, 페이징)"))
	@OperationErrorCodes({ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<PagedSuccessResponseEnvelope<MyRoundRecordResponse>> myRecords(
			@Parameter(description = "페이지 번호 (0부터 시작, 기본값 0)", example = "0")
			@RequestParam(name = "page", defaultValue = "0") int page,
			@Parameter(description = "페이지 크기 (기본값 10)", example = "10")
			@RequestParam(name = "size", defaultValue = "10") int size) {
		return ResponseEntity.ok(PagedSuccessResponseEnvelope.from(roundResultService.myRecords(memberId(), page, size)));
	}

	@GetMapping(value = "/{multigameId}/log", version = "1+")
	@Operation(summary = "Get my round log", description = """
			특정 라운드에서 현재 사용자의 신청 시도 로그(타임라인)를 시각순으로 조회합니다.
			상태가 전이되는 시점의 이벤트(ENQUEUED, SUCCESS, FAIL_SOLDOUT, FAIL_DUPLICATE)만 기록되며,
			각 이벤트는 대기열 순번(seq)과 그 시점의 입장 허용선(limit)을 포함합니다.
			미참여 라운드면 404(MULTIGAME_004)를 반환합니다.
			""", responses = @ApiResponse(responseCode = "200", description = "내 신청 시도 로그 (시각순)"))
	@OperationErrorCodes({ErrorCode.MULTIGAME_RESULT_NOT_FOUND, ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<MyRoundLogResponse>> myLog(
			@Parameter(description = "게임 식별자 T — 10분 단위 게임 시작 시각 (yyyyMMddHHmmss 14자리)", example = "20260801120000")
			@PathVariable @Pattern(regexp = "\\d{14}") String multigameId) {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(roundResultService.myLog(multigameId, memberId())));
	}

	private long memberId() {
		return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
	}
}
