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
			ErrorCode.SINGLEGAME_INVALID_TOTAL_COURSES,
			ErrorCode.SINGLEGAME_INVALID_DETAILS_COUNT,
			ErrorCode.SINGLEGAME_INVALID_REACTION_TIME,
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
			description = "과목 수별 전체/학과 랭킹을 조회합니다. 순위는 사람 기준이며, 1인당 최고 기록 1판으로 매깁니다 "
					+ "(동점자는 같은 순위, 다음 순위는 건너뜁니다). "
					+ "scope가 GLOBAL이면 전체 순위, DEPARTMENT면 학과 안 순위입니다. "
					+ "학과를 적으면 그 학과를 보고, 생략하면 요청자 본인 학과를 씁니다. "
					+ "학과가 없는 요청자는 DEPARTMENT를 쓸 수 없습니다(400 SINGLEGAME_INVALID_RANK_DEPARTMENT). "
					+ "응답의 department에 적용된 학과가 나갑니다(GLOBAL이면 null). "
					+ "myRank는 요청자 본인 최고 기록의 순위로 scope을 따릅니다(완료판이 없으면 null).", 
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
			ErrorCode.SINGLEGAME_INVALID_RANK_DEPARTMENT,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<RankingResponse>> getRankings(
			@Parameter(description = "과목 수 (1, 3, 6, 7, 8). 과목 수마다 랭킹이 따로 집계됩니다", example = "6", required = true)
			@RequestParam("totalCourses") int totalCourses,
			@Parameter(description = "조회 범위 (GLOBAL=전체 순위, DEPARTMENT=학과 내 순위)", example = "GLOBAL", required = true)
			@RequestParam("scope") String scope,
			@Parameter(description = "학과명. DEPARTMENT일 때 적으면 그 학과, 생략하면 요청자 본인 학과. 학과 없는 요청자는 DEPARTMENT 사용 불가", example = "컴퓨터공학과")
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
			description = "특정 게임 판의 상세 분석 결과를 조회합니다. 남의 판도 볼 수 있으며, 붙는 통계·순위는 전부 판 주인 기준입니다. "
					+ "gameId·isOwner(내 판 여부)·isMember(판 주인의 회원 여부)·totalCourses는 항상 나갑니다. "
					+ "record는 판 원본(tTotal·tEnterMain·completed·createdAt), "
					+ "globalRank는 전체 순위(rank·totalPersons·percentile, 순위는 사람 기준), "
					+ "departmentRank는 판 주인 학과 내 순위(department·rank·totalPersons·percentile)입니다. "
					+ "globalTimeline·departmentTimeline은 구간별 반응속도와 p10·p30·p50·p70 분포로 차트를 그리고, "
					+ "detail은 구간별 반응속도·상위 백분위·등급·전체/학과 분포로 성적표를 뿌립니다. "
					+ "feedbacks는 맞춤 피드백 2종입니다. "
					+ "게스트 조회에서는 departmentRank·departmentTimeline·feedbacks가 null이고 "
					+ "detail의 grade·학과 분포도 null이라 전체 순위·전체 분포만 받습니다. "
					+ "판 주인이 게스트면(학과 없음) departmentRank·departmentTimeline이 null입니다.",
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
