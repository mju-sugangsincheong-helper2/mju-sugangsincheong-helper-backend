package com.mjusugangsincheonghelper.latency.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LatencySubmitRequest {

	@NotNull
	@PositiveOrZero
	private Double medianMs;

	@NotNull
	@PositiveOrZero
	private Double maxMs;

	@NotNull
	@PositiveOrZero
	private Double minMs;

	@NotNull
	@PositiveOrZero
	private Double stdDevMs;

	@NotNull
	@Positive
	private Integer sampleCount;

	@NotEmpty
	private List<@PositiveOrZero Double> samples;
}
