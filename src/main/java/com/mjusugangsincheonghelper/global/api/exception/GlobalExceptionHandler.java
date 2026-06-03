package com.mjusugangsincheonghelper.global.api.exception;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.ErrorResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.exception.ErrorDetail.FieldViolation;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

	private final SystemConfigService systemConfigService;

	@ExceptionHandler(BaseException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleBaseException(BaseException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		log.warn("BaseException: code={}, message={}", errorCode.getCode(), errorCode.getMessage());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
		List<FieldViolation> fields = exception.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldViolation)
				.collect(Collectors.toList());

		ErrorCode errorCode = ErrorCode.GLOBAL_VALIDATION_ERROR;
		log.warn("Validation failed: fields={}", fields.size());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, fields, isExposeFieldDetails()));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleConstraintViolation(ConstraintViolationException exception) {
		List<FieldViolation> fields = exception.getConstraintViolations().stream()
				.map(this::toFieldViolation)
				.collect(Collectors.toList());

		ErrorCode errorCode = ErrorCode.GLOBAL_VALIDATION_ERROR;
		log.warn("Constraint violation: fields={}", fields.size());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, fields, isExposeFieldDetails()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMessageNotReadable(HttpMessageNotReadableException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_BAD_REQUEST;
		log.warn("Message not readable: {}", exception.getMessage());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponseEnvelope> handleUnexpected(Exception exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR;
		log.error("Unexpected error", exception);
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode));
	}

	private boolean isExposeFieldDetails() {
		return systemConfigService.getBoolean(SystemConfigService.EXPOSE_FIELD_DETAILS_KEY, true);
	}

	private FieldViolation toFieldViolation(FieldError fieldError) {
		return FieldViolation.builder()
				.field(fieldError.getField())
				.message(fieldError.getDefaultMessage())
				.build();
	}

	private FieldViolation toFieldViolation(ConstraintViolation<?> violation) {
		return FieldViolation.builder()
				.field(violation.getPropertyPath().toString())
				.message(violation.getMessage())
				.build();
	}
}
