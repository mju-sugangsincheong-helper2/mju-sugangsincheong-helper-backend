package com.mjusugangsincheonghelper.global.api.exception;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.ErrorResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.exception.ErrorDetail.FieldViolation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private final com.mjusugangsincheonghelper.system.service.SystemConfigService systemConfigService;

	public GlobalExceptionHandler(com.mjusugangsincheonghelper.system.service.SystemConfigService systemConfigService) {
		this.systemConfigService = systemConfigService;
	}

	private boolean isExposeErrorDetails() {
		try {
			return com.mjusugangsincheonghelper.system.definition.SettingDefinition.EXPOSE_ERROR_DETAILS.getFrom(systemConfigService);
		} catch (Exception e) {
			return false;
		}
	}

	@ExceptionHandler(BaseException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleBaseException(BaseException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		Throwable cause = exception.getCause();
		String detailMessage = exception.getDetailMessage();

		List<FieldViolation> details;
		if (detailMessage != null) {
			details = Collections.singletonList(FieldViolation.builder()
					.message(detailMessage)
					.build());
		} else if (cause != null) {
			details = Collections.singletonList(FieldViolation.builder()
					.message(cause.getClass().getSimpleName() + ": " + cause.getMessage())
					.build());
		} else {
			details = errorDetailFrom(errorCode);
		}

		log.warn("BaseException: code={}, message={}", errorCode.getCode(), errorCode.getMessage(), cause);
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, details, isExposeErrorDetails()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
		List<FieldViolation> details = exception.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldViolation)
				.collect(Collectors.toList());

		ErrorCode errorCode = ErrorCode.GLOBAL_VALIDATION_ERROR;
		log.warn("Validation failed: details={}", details.size());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, details, isExposeErrorDetails()));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleConstraintViolation(ConstraintViolationException exception) {
		List<FieldViolation> details = exception.getConstraintViolations().stream()
				.map(this::toFieldViolation)
				.collect(Collectors.toList());

		ErrorCode errorCode = ErrorCode.GLOBAL_VALIDATION_ERROR;
		log.warn("Constraint violation: details={}", details.size());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, details, isExposeErrorDetails()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMessageNotReadable(HttpMessageNotReadableException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_BAD_REQUEST;
		log.warn("Message not readable: {}", exception.getMessage());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, errorDetailFrom(exception), isExposeErrorDetails()));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMissingServletRequestParameter(MissingServletRequestParameterException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_BAD_REQUEST;
		log.warn("Missing request parameter: {}", exception.getMessage());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, errorDetailFrom(exception), isExposeErrorDetails()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_BAD_REQUEST;
		log.warn("Type mismatch: {}", exception.getMessage());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, errorDetailFrom(exception), isExposeErrorDetails()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleNoResourceFound(NoResourceFoundException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_NOT_FOUND;
		log.warn("Resource not found: {}", exception.getMessage());
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, errorDetailFrom(exception), isExposeErrorDetails()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponseEnvelope> handleUnexpected(Exception exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR;
		log.error("Unexpected error", exception);
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponseEnvelope.from(errorCode, errorDetailFrom(exception), isExposeErrorDetails()));
	}

	private List<FieldViolation> errorDetailFrom(ErrorCode errorCode) {
		return Collections.singletonList(FieldViolation.builder()
				.message(errorCode.getMessage())
				.build());
	}

	private List<FieldViolation> errorDetailFrom(Exception exception) {
		return Collections.singletonList(FieldViolation.builder()
				.message(exception.getClass().getSimpleName() + ": " + exception.getMessage())
				.build());
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
