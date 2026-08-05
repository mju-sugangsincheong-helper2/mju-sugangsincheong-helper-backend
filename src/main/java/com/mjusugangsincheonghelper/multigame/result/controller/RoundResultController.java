package com.mjusugangsincheonghelper.multigame.result.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.PagedSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.result.dto.RoundDetailResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.RoundSummaryResponse;
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

@Tag(name = "Multigame", description = "멀티게임 결과 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame/results")
@PreAuthorize("hasRole('GUEST')")
@Validated
public class RoundResultController {

	private final RoundResultService roundResultService;

	@GetMapping(version = "1+")
	@Operation(summary = "Get rounds", description = """
			종료된 라운드(게임)들의 결과 목록을 최신순으로 페이징 조회합니다. 각 항목은 게임 시작 시각
			(multigameId = T)과 참여자 수(participantCount)만 포함하며,
			처리 시계열과 내 참여 정보는 상세 API(GET /results/{multigameId})에서 확인할 수 있습니다.
			""", responses = @ApiResponse(responseCode = "200", description = "라운드 결과 목록 (최신순, 페이징)"))
	@OperationErrorCodes({ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<PagedSuccessResponseEnvelope<RoundSummaryResponse>> rounds(
			@Parameter(description = "페이지 번호 (0부터 시작, 기본값 0)", example = "0")
			@RequestParam(name = "page", defaultValue = "0") int page,
			@Parameter(description = "페이지 크기 (기본값 10)", example = "10")
			@RequestParam(name = "size", defaultValue = "10") int size) {
		return ResponseEntity.ok(PagedSuccessResponseEnvelope.from(roundResultService.rounds(page, size)));
	}

	@GetMapping(value = "/{multigameId}", version = "1+")
	@Operation(summary = "Get round detail", description = """
			특정 라운드의 상세 정보를 조회합니다. 게임 시작 시각(multigameId = T), 참여자 수(participantCount),
			과목당 배정된 정원(capacity), 현재 로그인한 사용자가 해당 라운드에 참여했는지(participated)를 반환하며,
			서버에 기록된 전체 처리 시계열(timeline: participantNo/subjectId/status/seq/limit/attemptedAt/mine)을 기록 시각순으로 함께 반환합니다.
			참여자는 실제 ID 대신 등장 순서대로 부여된 번호(participantNo 1, 2, 3...)로 구분되며,
			시계열에는 항상 전체 기록이 담기고 참여한 판이면 내 기록도 mine=true로 표시됩니다.
			존재하지 않는 라운드면 404(MULTIGAME_004)를 반환합니다.
			""", responses = @ApiResponse(responseCode = "200", description = "라운드 상세 (게임 시각 + 참여자 수 + 과목당 정원 + 참여 여부 + 처리 시계열)"))
	@OperationErrorCodes({ErrorCode.MULTIGAME_RESULT_NOT_FOUND, ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<RoundDetailResponse>> detail(
			@Parameter(description = "게임 식별자 T — 10분 단위 게임 시작 시각 (yyyyMMddHHmmss 14자리)", example = "20260801120000")
			@PathVariable(name = "multigameId") @Pattern(regexp = "\\d{14}") String multigameId) {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(roundResultService.roundDetail(multigameId, memberId())));
	}

	private long memberId() {
		return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
	}
}
