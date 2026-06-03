package com.mjusugangsincheonghelper.global.api.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	GLOBAL_BAD_REQUEST(HttpStatus.BAD_REQUEST, "GLOBAL_001", "Bad request."),
	GLOBAL_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "GLOBAL_002", "Validation failed."),
	GLOBAL_NOT_FOUND(HttpStatus.NOT_FOUND, "GLOBAL_003", "Resource not found."),
	GLOBAL_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_004", "Internal server error."),

	GLOBAL_SECURITY_UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "GLOBAL_SECURITY_001", "Unauthorized access."),
	GLOBAL_SECURITY_FORBIDDEN(HttpStatus.FORBIDDEN, "GLOBAL_SECURITY_002", "Access denied."),

	SYSTEM_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_001", "System config not found."),

	EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAMPLE_001", "Example not found."),
	EXAMPLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "EXAMPLE_002", "Example already exists.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
