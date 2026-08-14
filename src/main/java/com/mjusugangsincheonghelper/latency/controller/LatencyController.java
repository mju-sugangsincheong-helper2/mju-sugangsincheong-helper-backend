package com.mjusugangsincheonghelper.latency.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.PagedSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.latency.dto.LatencyDistributionResponse;
import com.mjusugangsincheonghelper.latency.dto.LatencyMyRecordResponse;
import com.mjusugangsincheonghelper.latency.dto.LatencySubmitRequest;
import com.mjusugangsincheonghelper.latency.dto.LatencySubmitResponse;
import com.mjusugangsincheonghelper.latency.service.LatencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Latency", description = "핑 테스트(응답속도 측정) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/latency")
public class LatencyController {

	private final LatencyService latencyService;

	@PostMapping(version = "1+")
	@PreAuthorize("hasRole('GUEST')")
	@Operation(
		summary = "Submit latency samples",
		description = "클라이언트에서 측정한 HTTP RTT 샘플 통계를 제출합니다.",
		responses = {
			@ApiResponse(responseCode = "201", description = "저장 성공")
		}
	)
	@OperationErrorCodes({
		ErrorCode.GLOBAL_VALIDATION_ERROR,
		ErrorCode.AUTH_MEMBER_NOT_FOUND,
		ErrorCode.LATENCY_EMPTY_SAMPLES,
		ErrorCode.LATENCY_INVALID_SAMPLE_VALUE,
		ErrorCode.LATENCY_SAMPLE_COUNT_MISMATCH
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<LatencySubmitResponse>> submit(
		@Valid @RequestBody LatencySubmitRequest request,
		Authentication authentication
	) {
		Long memberId = Long.parseLong(authentication.getName());
		boolean isMember = authentication.getAuthorities().stream()
			.anyMatch(a -> a.getAuthority().equals("ROLE_MEMBER") || a.getAuthority().equals("ROLE_ADMIN"));

		LatencySubmitResponse response = latencyService.submit(memberId, request, isMember);
		return ResponseEntity.status(201).body(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/my", version = "1+")
	@PreAuthorize("hasRole('GUEST')")
	@Operation(
		summary = "Get my latency history",
		description = "내가 제출한 핑 테스트 결과를 최신순으로 페이징 조회합니다.",
		responses = {
			@ApiResponse(responseCode = "200", description = "조회 성공")
		}
	)
	@OperationErrorCodes({
		ErrorCode.AUTH_MEMBER_NOT_FOUND
	})
	public ResponseEntity<PagedSuccessResponseEnvelope<LatencyMyRecordResponse>> myHistory(
		@PageableDefault(size = 20) Pageable pageable,
		Authentication authentication
	) {
		Long memberId = Long.parseLong(authentication.getName());
		Page<LatencyMyRecordResponse> page = latencyService.getMyHistory(memberId, pageable);
		return ResponseEntity.ok(PagedSuccessResponseEnvelope.from(page));
	}
}
