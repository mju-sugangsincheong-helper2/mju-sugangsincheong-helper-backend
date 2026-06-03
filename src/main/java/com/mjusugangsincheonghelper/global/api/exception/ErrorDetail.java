package com.mjusugangsincheonghelper.global.api.exception;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ErrorDetail {

	private final String code;
	private final String message;
	private final List<FieldViolation> fields;

	public static ErrorDetail from(ErrorCode errorCode) {
		return ErrorDetail.builder()
				.code(errorCode.getCode())
				.message(errorCode.getMessage())
				.build();
	}

	public static ErrorDetail from(ErrorCode errorCode, List<FieldViolation> fields) {
		return from(errorCode, fields, true);
	}

	public static ErrorDetail from(ErrorCode errorCode, List<FieldViolation> fields, boolean exposeFieldDetails) {
		return ErrorDetail.builder()
				.code(errorCode.getCode())
				.message(errorCode.getMessage())
				.fields(exposeFieldDetails ? fields : null)
				.build();
	}

	@Getter
	@Builder
	@AllArgsConstructor
	public static class FieldViolation {

		private final String field;
		private final String message;
	}
}
