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
			종료된 라운드(게임)들의 결과 목록을 최신순으로 페이징 조회합니다. 각 항목은 라운드 메타
			(참여자 수 participantCount, 과목별 좌석 수 capacity, 결과 영속화 시각 createdAt)를 포함하며,
			과목별 상세 집계와 내 참여 정보는 상세 API(GET /results/{multigameId})에서 확인할 수 있습니다.
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
			특정 라운드의 상세 정보를 조회합니다. 라운드 메타(참여자 수 participantCount, 좌석 수 capacity,
			결과 영속화 시각 createdAt)와 과목 1~6 각각의 신청 수(applied), 성공 수(succeeded),
			경쟁률(competitionRate = applied / capacity)을 반환하며, 현재 로그인한 사용자가 해당 라운드에
			참여했는지(participated)와 참여했다면 내 최종 결과 목록(myResults: 과목별 subjectId/status/createdAt)과
			신청 시도 타임라인(myLog: 과목별 ENQUEUED/SUCCESS/FAIL_SOLDOUT/FAIL_DUPLICATE)을 함께 반환합니다.
			미참여 라운드는 participated=false, myResults=[], myLog=[] 로 반환되어 프론트에서 자신의 결과를
			특별히 처리할 수 있습니다. 개인 식별 정보(memberId)는 본인 데이터 외 포함되지 않으며,
			존재하지 않는 라운드면 404(MULTIGAME_004)를 반환합니다.
			""", responses = @ApiResponse(responseCode = "200", description = "라운드 상세 (메타 + 분석서 + 내 참여 정보)"))
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
