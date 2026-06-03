package com.mjusugangsincheonghelper.global.api.envelope;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.ErrorDetail;
import com.mjusugangsincheonghelper.global.api.exception.ErrorDetail.FieldViolation;
import com.mjusugangsincheonghelper.global.api.meta.ResponseMeta;
import com.mjusugangsincheonghelper.global.api.support.MetaGenerator;
import java.util.List;
import lombok.Getter;

@Getter
public class ErrorResponseEnvelope extends ResponseEnvelope {

	private final ErrorDetail error;

	private ErrorResponseEnvelope(ResponseMeta meta, ErrorDetail error) {
		super(meta);
		this.error = error;
	}

	public static ErrorResponseEnvelope from(ErrorCode errorCode) {
		return new ErrorResponseEnvelope(MetaGenerator.generate(), ErrorDetail.from(errorCode));
	}

	public static ErrorResponseEnvelope from(ErrorCode errorCode, List<FieldViolation> fields) {
		return new ErrorResponseEnvelope(MetaGenerator.generate(), ErrorDetail.from(errorCode, fields));
	}

	public static ErrorResponseEnvelope from(ErrorCode errorCode, List<FieldViolation> fields, boolean exposeFieldDetails) {
		return new ErrorResponseEnvelope(MetaGenerator.generate(), ErrorDetail.from(errorCode, fields, exposeFieldDetails));
	}
}
