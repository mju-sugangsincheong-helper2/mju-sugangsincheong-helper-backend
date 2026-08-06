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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
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

	private final boolean exposeErrorDetails;

	public GlobalExceptionHandler(
			@org.springframework.beans.factory.annotation.Value("${app.expose-error-details:false}") boolean exposeErrorDetails) {
		this.exposeErrorDetails = exposeErrorDetails;
	}

	private boolean isExposeErrorDetails() {
		return exposeErrorDetails;
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
		return errorResponse(errorCode, details);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
		List<FieldViolation> details = exception.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldViolation)
				.collect(Collectors.toList());

		ErrorCode errorCode = ErrorCode.GLOBAL_VALIDATION_ERROR;
		log.warn("Validation failed: details={}", details.size());
		return errorResponse(errorCode, details);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleConstraintViolation(ConstraintViolationException exception) {
		List<FieldViolation> details = exception.getConstraintViolations().stream()
				.map(this::toFieldViolation)
				.collect(Collectors.toList());

		ErrorCode errorCode = ErrorCode.GLOBAL_VALIDATION_ERROR;
		log.warn("Constraint violation: details={}", details.size());
		return errorResponse(errorCode, details);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMessageNotReadable(HttpMessageNotReadableException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_BAD_REQUEST;
		log.warn("Message not readable: {}", exception.getMessage());
		return errorResponse(errorCode, errorDetailFrom(exception));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMissingServletRequestParameter(MissingServletRequestParameterException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_BAD_REQUEST;
		log.warn("Missing request parameter: {}", exception.getMessage());
		return errorResponse(errorCode, errorDetailFrom(exception));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_BAD_REQUEST;
		log.warn("Type mismatch: {}", exception.getMessage());
		return errorResponse(errorCode, errorDetailFrom(exception));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleNoResourceFound(NoResourceFoundException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_NOT_FOUND;
		log.warn("Resource not found: {}", exception.getMessage());
		return errorResponse(errorCode, errorDetailFrom(exception));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleAccessDenied(AccessDeniedException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_SECURITY_FORBIDDEN;
		log.warn("Access denied: {}", exception.getMessage());
		return errorResponse(errorCode, errorDetailFrom(exception));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleIllegalArgument(IllegalArgumentException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_BAD_REQUEST;
		log.warn("Illegal argument: {}", exception.getMessage());
		return errorResponse(errorCode, errorDetailFrom(exception));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorResponseEnvelope> handleIllegalState(IllegalStateException exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_BAD_REQUEST;
		log.warn("Illegal state: {}", exception.getMessage());
		return errorResponse(errorCode, errorDetailFrom(exception));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponseEnvelope> handleUnexpected(Exception exception) {
		ErrorCode errorCode = ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR;
		log.error("Unexpected error", exception);
		return errorResponse(errorCode, errorDetailFrom(exception));
	}

	private ResponseEntity<ErrorResponseEnvelope> errorResponse(ErrorCode code, List<FieldViolation> details) {
		// Content-Type을 명시적으로 JSON 지정: actuator처럼 produces가 text/plain인 엔드포인트의
		// 예외를 잡았을 때 preset Content-Type 때문에 JSON을 못 쓰고 HttpMessageNotWritableException이
		// 나는 것을 방지한다.
		return ResponseEntity.status(code.getStatus())
				.contentType(MediaType.APPLICATION_JSON)
				.body(ErrorResponseEnvelope.from(code, details, isExposeErrorDetails()));
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
