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
	@Operation(summary = "Get department rankings", description = """
			학과별 멀티게임 참가 현황과 성공률 순위를 조회합니다. 참여 수 목록(participation:
			department, participantCount)과 상위 70% 평균 성공률 목록(performance: department,
			top70AvgSuccessRate, participantCount), 현재 사용자 소속 학과의 순위(myDepartment:
			participationRank/performanceRank/participantCount/top70AvgSuccessRate)를 반환합니다.
			학과별 성공률이 낮은 하위 30%를 제외한 상위 70% 참여자의 평균 성공률로 순위를 산출하며,
			소속 학과가 없거나 집계 대상이 아니면 myDepartment는 null입니다. 로그인한 사용자
			(MEMBER 이상)만 호출할 수 있고, 순위의 기반이 되는 최종 결과는 결과 조회 API
			(GET /results)에서 확인할 수 있습니다. 비즈니스 예외는 발생하지 않으며 서버 오류 시에만
			500(GLOBAL_004)이 반환됩니다.
			""", responses = @ApiResponse(responseCode = "200", description = "학과별 참가 수 및 상위 70% 평균 성공률 순위 — participation, performance, myDepartment"))
	@OperationErrorCodes({ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR})
	public ResponseEntity<SingleSuccessResponseEnvelope<MultigameRankingResponse>> rankings() {
		long memberId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(rankingService.rankings(memberId)));
	}
}
