package com.mjusugangsincheonghelper.system.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.system.dto.SystemConfigResponse;
import com.mjusugangsincheonghelper.system.dto.SystemConfigUpdateRequest;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System", description = "시스템 설정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/system/configs")
public class SystemConfigController {

	private final SystemConfigService systemConfigService;

	@GetMapping(value = "/{key}", version = "1+")
	@Operation(
			summary = "System config detail",
			description = "시스템 설정 단건 조회 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.SYSTEM_CONFIG_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<SystemConfigResponse>> find(
			@Parameter(description = "설정 키", example = "expose_error_details")
			@PathVariable String key) {
		SystemConfigResponse response = systemConfigService.find(key);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@PutMapping(value = "/{key}", version = "1+")
	@Operation(
			summary = "System config update",
			description = "시스템 설정 수정 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "수정 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.SYSTEM_CONFIG_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<SystemConfigResponse>> update(
			@Parameter(description = "설정 키", example = "expose_error_details")
			@PathVariable String key,
			@Valid @RequestBody SystemConfigUpdateRequest request) {
		SystemConfigResponse response = systemConfigService.update(key, request);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}
}
