package com.mjusugangsincheonghelper.global.api.exception;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

	private final ErrorCode errorCode;
	private final String detailMessage;

	public BaseException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.detailMessage = null;
	}

	public BaseException(ErrorCode errorCode, String detailMessage) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.detailMessage = detailMessage;
	}

	public BaseException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
		this.detailMessage = null;
	}
}
