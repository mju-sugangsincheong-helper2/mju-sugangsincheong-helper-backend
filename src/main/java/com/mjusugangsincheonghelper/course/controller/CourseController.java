package com.mjusugangsincheonghelper.course.controller;

import com.mjusugangsincheonghelper.course.dto.CourseSectionDeleteResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportRequest;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionResponse;
import com.mjusugangsincheonghelper.course.service.CourseService;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course", description = "강좌 정보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/course/sections")
public class CourseController {

	private final CourseService courseService;
	private final SystemConfigService systemConfigService;

	@PostMapping(version = "1+")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(
			summary = "Import course sections",
			description = "강좌 정보를 일괄 등록/갱신합니다. 같은 coursecls+term 이 존재하면 갱신됩니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "등록 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<CourseSectionImportResponse>> importSections(
			@Parameter(description = "등록할 강좌 목록", required = true)
			@Valid @RequestBody List<CourseSectionImportRequest> items) {
		CourseSectionImportResponse response = courseService.importSections(items);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@DeleteMapping(version = "1+")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(
			summary = "Delete course sections by term",
			description = "특정 학기의 강좌 정보를 일괄 삭제합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "삭제 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<CourseSectionDeleteResponse>> deleteSections(
			@Parameter(description = "삭제할 학기 (예: 202515)", example = "202515", required = true)
			@RequestParam("term") String term) {
		CourseSectionDeleteResponse response = courseService.deleteSectionsByTerm(term);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(version = "1+")
	@PreAuthorize("hasRole('GUEST')")
	@Operation(
			summary = "List course sections",
			description = "강좌 목록을 조회합니다. term 파라미터가 없으면 현재 학기(current_term)를 조회합니다.",
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
	public ResponseEntity<SingleSuccessResponseEnvelope<List<CourseSectionResponse>>> findSections(
			@Parameter(description = "조회할 학기 (예: 202515), 없으면 current_term 사용", example = "202515")
			@RequestParam(name = "term", required = false) String term) {
		String effectiveTerm = (term != null && !term.isBlank()) ? term : systemConfigService.getCurrentTerm();
		List<CourseSectionResponse> response = courseService.findSections(effectiveTerm);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}
}
