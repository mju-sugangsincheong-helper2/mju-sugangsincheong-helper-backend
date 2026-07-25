package com.mjusugangsincheonghelper.multigame.my.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.PagedSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.my.dto.MyHistoryResponse;
import com.mjusugangsincheonghelper.multigame.my.service.MultigameMyHistoryService;
import com.mjusugangsincheonghelper.multigame.stats.dto.MyStatsResponse;
import com.mjusugangsincheonghelper.multigame.stats.service.MultigameMyStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Multigame", description = "멀티게임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame")
@PreAuthorize("hasRole('GUEST')")
public class MultigameMyController {

	private final MultigameMyHistoryService myHistoryService;
	private final MultigameMyStatsService myStatsService;

	@GetMapping(value = "/my/history", version = "1+")
	@Operation(
			summary = "Get my game history",
			description = "내 멀티게임 참여 기록 목록 조회 API (페이징)",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<PagedSuccessResponseEnvelope<MyHistoryResponse>> getMyHistory(
			@PageableDefault(size = 10) Pageable pageable) {
		Long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		Page<MyHistoryResponse> history = myHistoryService.getMyHistory(memberId, pageable);
		return ResponseEntity.ok(PagedSuccessResponseEnvelope.from(history));
	}

	@GetMapping(value = "/my/stats", version = "1+")
	@Operation(
			summary = "Get my game stats",
			description = "내 멀티게임 참여 통계 요약 조회 API",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<MyStatsResponse>> getMyStats() {
		Long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		MyStatsResponse stats = myStatsService.getMyStats(memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(stats));
	}
}
