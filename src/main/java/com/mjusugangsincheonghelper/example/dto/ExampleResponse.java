package com.mjusugangsincheonghelper.example.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ExampleResponse {

	private final String message;
	private final Instant timestamp;

	public static ExampleResponse of(String message) {
		return ExampleResponse.builder()
				.message(message)
				.timestamp(Instant.now())
				.build();
	}
}
