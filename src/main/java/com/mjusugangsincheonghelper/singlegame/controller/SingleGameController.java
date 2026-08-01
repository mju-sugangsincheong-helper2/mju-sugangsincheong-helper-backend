package com.mjusugangsincheonghelper.singlegame.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.PagedSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.singlegame.dto.DepartmentsResponse;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import com.mjusugangsincheonghelper.singlegame.service.SingleGameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SingleGame", description = "싱글 게임 API")
@RestController
@PreAuthorize("hasRole('GUEST')")
@RequiredArgsConstructor
@RequestMapping("/api/{version}/singlegame")
public class SingleGameController {

	private final SingleGameService singleGameService;

	@PostMapping(version = "1+")
	@Operation(
			summary = "Save game result",
			description = "싱글 게임 결과를 저장합니다.",
			responses = {
					@ApiResponse(
							responseCode = "201",
							description = "저장 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.AUTH_MEMBER_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<SingleGameSaveResponse>> saveGame(
			@Parameter(description = "게임 결과 데이터", required = true)
			@Valid @RequestBody SingleGameSaveRequest request) {
		Long memberId = getCurrentMemberId();
		SingleGameSaveResponse response = singleGameService.saveGame(memberId, request);
		return ResponseEntity.status(201).body(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/rank", version = "1+")
	@Operation(
			summary = "Get rankings",
			description = "과목 수별 전체/학과 랭킹을 조회합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.AUTH_MEMBER_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<RankingResponse>> getRankings(
			@Parameter(description = "과목 수 (1, 3, 6, 7, 8)", example = "6", required = true)
			@RequestParam("totalCourses") int totalCourses,
			@Parameter(description = "조회 범위 (GLOBAL or DEPARTMENT)", example = "GLOBAL", required = true)
			@RequestParam("scope") String scope,
			@Parameter(description = "학과명 (DEPARTMENT일 때, 없으면 본인 학과)", example = "컴퓨터공학과")
			@RequestParam(value = "department", required = false) String department) {
		Long memberId = getCurrentMemberId();
		RankingResponse response = singleGameService.getRankings(totalCourses, scope, department, memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/departments", version = "1+")
	@Operation(
			summary = "Get departments",
			description = "싱글게임 데이터에 존재하는 모든 학과 목록을 조회합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<DepartmentsResponse>> getDepartments() {
		DepartmentsResponse response = singleGameService.getDepartments();
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/my", version = "1+")
	@Operation(
			summary = "Get my records",
			description = "내 게임 기록 목록을 페이징하여 조회합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.AUTH_MEMBER_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<PagedSuccessResponseEnvelope<MyRecordResponse>> getMyRecords(
			@Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
			@RequestParam(name = "page", defaultValue = "0") int page,
			@Parameter(description = "페이지 크기", example = "10")
			@RequestParam(name = "size", defaultValue = "10") int size) {
		Long memberId = getCurrentMemberId();
		Page<MyRecordResponse> response = singleGameService.getMyRecords(memberId, page, size);
		return ResponseEntity.ok(PagedSuccessResponseEnvelope.from(response));
	}

	@GetMapping(value = "/{gameId}/analysis", version = "1+")
	@Operation(
			summary = "Get game analysis",
			description = "특정 게임 판의 상세 분석 결과를 조회합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.SINGLEGAME_GAME_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<AnalysisResponse>> getAnalysis(
			@Parameter(description = "게임 ID", example = "1234", required = true)
			@PathVariable("gameId") Long gameId) {
		Long memberId = getCurrentMemberId();
		AnalysisResponse response = singleGameService.getAnalysis(gameId, memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	private Long getCurrentMemberId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return (Long) authentication.getPrincipal();
	}
}
