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

	private int tClickCourse;

	private int tClickYes;

	private int tClickOk;
}
