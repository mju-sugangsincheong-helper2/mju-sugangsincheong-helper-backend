package com.mjusugangsincheonghelper.global.api.exception;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

	private final ErrorCode errorCode;

	public BaseException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
}
