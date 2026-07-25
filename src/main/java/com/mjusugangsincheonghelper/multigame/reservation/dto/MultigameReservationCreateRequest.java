package com.mjusugangsincheonghelper.multigame.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultigameReservationCreateRequest {

	@NotBlank(message = "multigameId is required.")
	@Pattern(regexp = "\\d{14}", message = "multigameId must be 14 digits (yyyyMMddHHmmss).")
	private String multigameId;
}
