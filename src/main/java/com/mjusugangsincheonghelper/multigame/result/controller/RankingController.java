package com.mjusugangsincheonghelper.multigame.result.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameRankingResponse;
import com.mjusugangsincheonghelper.multigame.result.service.RankingService;
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

@Tag(name = "Multigame", description = "멀티게임 랭킹 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame/rankings")
@PreAuthorize("hasRole('MEMBER')")
public class RankingController {

	private final RankingService rankingService;

	@GetMapping(version = "1+")
	@Operation(summary = "Get department rankings", description = "학과별 참가 수 및 상위 70% 평균 성공률 순위를 조회합니다.", responses = @ApiResponse(responseCode = "200", description = "조회 성공"))
	@OperationErrorCodes({ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<MultigameRankingResponse>> rankings() {
		long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(rankingService.rankings(memberId)));
	}
}
