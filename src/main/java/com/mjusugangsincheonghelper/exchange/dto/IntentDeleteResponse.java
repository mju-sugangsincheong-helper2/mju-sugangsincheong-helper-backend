package com.mjusugangsincheonghelper.exchange.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class IntentDeleteResponse {

	private final String message;
	private final Instant timestamp;
	private final Long intentId;
	private final boolean isDeleted;
}
