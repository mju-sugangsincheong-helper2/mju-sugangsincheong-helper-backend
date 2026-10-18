package com.mjusugangsincheonghelper.exchange.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class IntentDeleteResponse {

	private Long intentId;

	private boolean deleted;

	private Instant deletedAt;
}
