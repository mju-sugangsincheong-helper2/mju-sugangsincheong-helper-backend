package com.mjusugangsincheonghelper.global.api.envelope;

import com.mjusugangsincheonghelper.global.api.meta.ResponseMeta;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ResponseEnvelope {

	private final ResponseMeta meta;
}
