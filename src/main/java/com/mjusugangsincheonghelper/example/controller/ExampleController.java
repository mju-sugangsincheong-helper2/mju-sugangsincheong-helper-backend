package com.mjusugangsincheonghelper.example.controller;

import com.mjusugangsincheonghelper.example.dto.ExampleCreateRequest;
import com.mjusugangsincheonghelper.example.dto.ExampleDetailResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleEchoRequest;
import com.mjusugangsincheonghelper.example.dto.ExamplePageItem;
import com.mjusugangsincheonghelper.example.dto.ExampleResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleUpdateRequest;
import com.mjusugangsincheonghelper.example.service.ExampleService;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.PagedSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Example", description = "예제 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/example")
public class ExampleController {

	private final ExampleService exampleService;

	@GetMapping(value = "/hello", version = "1+")
	@Operation(
			summary = "Example hello",
			description = "응답 봉투와 메타데이터 확인용 API",
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
	public ResponseEntity<SingleSuccessResponseEnvelope<ExampleResponse>> hello(
			@Parameter(description = "인사할 이름", example = "world")
			@RequestParam(name = "name", defaultValue = "world") String name) {
		ExampleResponse response = exampleService.hello(name);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@PostMapping(value = "/echo", version = "1+")
	@Operation(
			summary = "Example echo",
			description = "요청 검증 및 단건 응답 확인용 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "에코 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<ExampleResponse>> echo(
			@Valid @RequestBody ExampleEchoRequest request) {
		ExampleResponse response = exampleService.echo(request);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@PostMapping(version = "1+")
	@Operation(
			summary = "Example create",
			description = "예제 엔티티 생성 API",
			responses = {
					@ApiResponse(
							responseCode = "201",
							description = "생성 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<ExampleDetailResponse>> create(
			@Valid @RequestBody ExampleCreateRequest request) {
		ExampleDetailResponse response = exampleService.create(request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/{id}", version = "1+")
	@Operation(
			summary = "Example detail",
			description = "예제 엔티티 단건 조회 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<ExampleDetailResponse>> detail(
			@Parameter(description = "예제 ID", example = "1")
			@PathVariable("id") Long id) {
		ExampleDetailResponse response = exampleService.findById(id);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/page", version = "1+")
	@Operation(
			summary = "Example page",
			description = "예제 엔티티 페이징 조회 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_BAD_REQUEST,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<PagedSuccessResponseEnvelope<ExamplePageItem>> page(
			@Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
			@RequestParam(name = "page", defaultValue = "0") int page,
			@Parameter(description = "페이지 크기", example = "10")
			@RequestParam(name = "size", defaultValue = "10") int size) {
		Page<ExamplePageItem> response = exampleService.list(page, size);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(PagedSuccessResponseEnvelope.from(response));
	}

	@PutMapping(value = "/{id}", version = "1+")
	@Operation(
			summary = "Example update",
			description = "예제 엔티티 수정 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "수정 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<ExampleDetailResponse>> update(
			@Parameter(description = "예제 ID", example = "1")
			@PathVariable("id") Long id,
			@Valid @RequestBody ExampleUpdateRequest request) {
		ExampleDetailResponse response = exampleService.update(id, request);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.of(response));
	}

	@DeleteMapping(value = "/{id}", version = "1+")
	@Operation(
			summary = "Example delete",
			description = "예제 엔티티 삭제(비활성화) API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "삭제 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<Void>> delete(
			@Parameter(description = "예제 ID", example = "1")
			@PathVariable("id") Long id) {
		exampleService.delete(id);
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.empty());
	}

	@GetMapping(value = "/error", version = "1+")
	@Operation(
			summary = "Example error",
			description = "에러 응답 봉투 확인용 API",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "정상 응답 (에러 발생 없음)"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<Void>> error() {
		exampleService.throwNotFound();
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(SingleSuccessResponseEnvelope.empty());
	}
}
