package com.mjusugangsincheonghelper.diagnostics.controller;

import com.mjusugangsincheonghelper.diagnostics.dto.QueryTimingResponse;
import com.mjusugangsincheonghelper.diagnostics.dto.SeedResultResponse;
import com.mjusugangsincheonghelper.diagnostics.service.DiagnosticsService;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * dev 전용 진단 API 진입점. 앞으로 추가되는 쿼리 속도 측정·디버깅용 API는
 * 도메인별 컨트롤러가 아닌 여기에 엔드포인트로 추가한다.
 *
 * <p>{@code @Profile("dev")}이므로 prod에서는 빈 자체가 등록되지 않아(404)
 * 실수로 운영에 노출될 수 없다.</p>
 */
@Tag(name = "Diagnostics", description = "진단 API (dev 환경 전용, prod 미등록)")
@Profile("dev")
@PreAuthorize("hasRole('GUEST')")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/diagnostics")
public class DiagnosticsController {

	private final DiagnosticsService diagnosticsService;

	@GetMapping(value = "/singlegame/dept-sequence-stats", version = "1+")
	@Operation(
			summary = "Time dept sequence stats query (dev only)",
			description = "findDeptSequencePercentileStats 쿼리를 전 학과에 대해 실행 시간을 측정합니다. "
					+ "학과별 내역이 departments에 담기며, min/avg/max는 전체 실행 기준입니다. "
					+ "Repository 직접 호출(통계 캐시 우회)이므로 순수 DB 실행 시간입니다. (dev 환경 전용)",
			responses = {
					@ApiResponse(responseCode = "200", description = "측정 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<QueryTimingResponse>> timeDeptSequenceStats(
			@Parameter(description = "과목 수", example = "6", required = true)
			@RequestParam("totalCourses") int totalCourses,
			@Parameter(description = "워밍업 실행 횟수 (0~5, 첫 학과에서만 수행)", example = "1")
			@RequestParam(name = "warmup", defaultValue = "1") int warmup,
			@Parameter(description = "본 측정 반복 횟수 (1~10)", example = "3")
			@RequestParam(name = "repeats", defaultValue = "3") int repeats) {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(
				diagnosticsService.timeDeptSequenceStats(totalCourses, warmup, repeats)));
	}

	@PostMapping(value = "/singlegame/seed", version = "1+")
	@Operation(
			summary = "Seed single games (dev only)",
			description = "쿼리 속도 테스트용 싱글게임 데이터를 대량으로 쌓습니다. 게임 수와 과목 수만 지정하면 학과·멤버·분포는 자동으로 분산 생성됩니다. "
					+ "완료 게임(is_completed=TRUE)만 만들며, 20개 학과에 게임을 균등 분산하고 학과마다 실력 분포를 다르게 주어 학과별 통계가 유의미하게 갈립니다. "
					+ "대량 적재는 수 분 소요될 수 있으며 진행률은 서버 로그로 확인할 수 있습니다. "
					+ "적재 후 통계 캐시를 전체 무효화하므로 바로 analysis로 확인할 수 있습니다. (dev 환경 전용)",
			responses = {
					@ApiResponse(responseCode = "200", description = "적재 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.SINGLEGAME_INVALID_TOTAL_COURSES,
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<SeedResultResponse>> seedSingleGames(
			@Parameter(description = "쌓을 게임 수 (1~1000000)", example = "100000", required = true)
			@RequestParam("count") int count,
			@Parameter(description = "과목 수 (1, 3, 6, 7, 8)", example = "6", required = true)
			@RequestParam("totalCourses") int totalCourses) {
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(
				diagnosticsService.seedSingleGames(count, totalCourses)));
	}
}
