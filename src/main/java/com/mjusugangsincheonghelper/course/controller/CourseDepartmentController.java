package com.mjusugangsincheonghelper.course.controller;

import com.mjusugangsincheonghelper.course.dto.CourseDepartmentResponse;
import com.mjusugangsincheonghelper.course.service.CourseService;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course", description = "강좌 정보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/course/department")
public class CourseDepartmentController {

	private final CourseService courseService;
	private final SystemConfigService systemConfigService;

	@GetMapping(version = "1+")
	@PreAuthorize("permitAll()") // 공개 GET API (PUBLIC_GET_URLS에 등록됨)
	@Operation(
			summary = "List departments",
			description = "학기별 개설 강좌의 학과 목록을 반환합니다. deptcd(학과 코드), deptnm(학과명), campusdiv(캠퍼스 코드)를 중복 없이 학과명 오름차순으로 반환합니다. " +
					"term 파라미터가 없으면 캐시에 저장된 current_term 설정값을 기준으로 조회하며, 해당 학기에 강좌 데이터가 없으면 직전 학기(202620 → 202610, 202625 → 202615, 202610 → 202520)로 순차 폴백하여 가장 최근 데이터가 있는 학기를 사용합니다. " +
					"20회 폴백 이후에도 데이터가 없으면 빈 목록을 반환합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "학과 목록 (deptcd, deptnm, campusdiv, 학과명 오름차순)"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<List<CourseDepartmentResponse>>> findDepartments(
			@Parameter(description = "조회할 학기 (예: 202515), 없으면 캐시된 current_term 사용", example = "202515")
			@RequestParam(name = "term", required = false) String term) {
		String effectiveTerm = (term != null && !term.isBlank()) ? term : systemConfigService.getCurrentTerm();
		List<CourseDepartmentResponse> response = courseService.findDepartments(effectiveTerm);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}
}
