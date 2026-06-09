package com.mjusugangsincheonghelper.singlegame.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SingleGameDetailRequest {

	@Min(1)
	private int sequence;

	@Min(0)
	private int tClickCourse;

	@Min(0)
	private int tClickYes;

	@Min(0)
	private int tClickOk;
}
