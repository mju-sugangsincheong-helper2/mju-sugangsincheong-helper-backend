package com.mjusugangsincheonghelper.multigame.result.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.PagedSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundRecordResponse;
import com.mjusugangsincheonghelper.multigame.result.service.RoundResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
			페이지네이션 단위는 라운드이며, 각 항목은 게임 시작 시각(multigameId = T), 참여자 수(participantCount),
			자신이 6개 과목 중 성공한 과목 수(successCount)만 노출합니다. 처리 시계열은
			상세 API(GET /results/{multigameId})에서 확인할 수 있습니다.
			""", responses = @ApiResponse(responseCode = "200", description = "내 참여 이력 목록 (최신순, 페이징)"))
	@OperationErrorCodes({ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<PagedSuccessResponseEnvelope<MyRoundRecordResponse>> myRecords(
			@Parameter(description = "페이지 번호 (0부터 시작, 기본값 0)", example = "0")
			@RequestParam(name = "page", defaultValue = "0") int page,
			@Parameter(description = "페이지 크기 (기본값 10)", example = "10")
			@RequestParam(name = "size", defaultValue = "10") int size) {
		return ResponseEntity.ok(PagedSuccessResponseEnvelope.from(roundResultService.myRecords(memberId(), page, size)));
	}

	private long memberId() {
		return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
	}
}
