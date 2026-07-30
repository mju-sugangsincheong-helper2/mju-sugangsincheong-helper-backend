package com.mjusugangsincheonghelper.exchange.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class IntentDeleteResponse {

	private Long intentId;

	@JsonProperty("isDeleted")
	private boolean isDeleted;

	private Instant deletedAt;
}
