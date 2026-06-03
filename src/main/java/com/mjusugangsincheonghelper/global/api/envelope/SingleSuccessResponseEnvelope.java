package com.mjusugangsincheonghelper.global.api.envelope;

import com.mjusugangsincheonghelper.global.api.meta.ResponseMeta;
import com.mjusugangsincheonghelper.global.api.support.MetaGenerator;
import lombok.Getter;

@Getter
public class SingleSuccessResponseEnvelope<T> extends ResponseEnvelope {

	private final T data;

	private SingleSuccessResponseEnvelope(ResponseMeta meta, T data) {
		super(meta);
		this.data = data;
	}

	public static <T> SingleSuccessResponseEnvelope<T> of(T data) {
		return new SingleSuccessResponseEnvelope<>(MetaGenerator.generate(), data);
	}

	public static SingleSuccessResponseEnvelope<Void> empty() {
		return new SingleSuccessResponseEnvelope<>(MetaGenerator.generate(), null);
	}
}
