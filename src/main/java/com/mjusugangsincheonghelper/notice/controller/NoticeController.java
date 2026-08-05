package com.mjusugangsincheonghelper.notice.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.notice.dto.NoticeRequest;
import com.mjusugangsincheonghelper.notice.dto.NoticeResponse;
import com.mjusugangsincheonghelper.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notice", description = "공지사항 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/notices")
public class NoticeController {

	private final NoticeService noticeService;

	@GetMapping(version = "1+")
	@PreAuthorize("permitAll()") // 공개 GET API (PUBLIC_GET_URLS에 등록됨)
	@Operation(
			summary = "Public notice list",
			description = "공개 공지사항 목록을 최신순으로 조회합니다.",
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
	public ResponseEntity<SingleSuccessResponseEnvelope<List<NoticeResponse>>> findAll() {
		List<NoticeResponse> response = noticeService.findAll();
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@PostMapping(version = "1+")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(
			summary = "Create notice",
			description = "공지사항을 등록하고 전체 사용자에게 푸시 알림을 발송합니다.",
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
	public ResponseEntity<SingleSuccessResponseEnvelope<NoticeResponse>> create(
			@Valid @RequestBody NoticeRequest request) {
		NoticeResponse response = noticeService.create(request);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@PutMapping(value = "/{id}", version = "1+")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(
			summary = "Update notice",
			description = "공지사항을 수정합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "수정 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.NOTICE_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<NoticeResponse>> update(
			@Parameter(description = "공지 ID", example = "1")
			@PathVariable("id") Long id,
			@Valid @RequestBody NoticeRequest request) {
		NoticeResponse response = noticeService.update(id, request);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@DeleteMapping(value = "/{id}", version = "1+")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(
			summary = "Delete notice",
			description = "공지사항을 삭제합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "삭제 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.NOTICE_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<Void>> delete(
			@Parameter(description = "공지 ID", example = "1")
			@PathVariable("id") Long id) {
		noticeService.delete(id);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.empty());
	}
}