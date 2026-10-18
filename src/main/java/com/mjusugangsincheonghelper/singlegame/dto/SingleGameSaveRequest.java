package com.mjusugangsincheonghelper.singlegame.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SingleGameSaveRequest {

	@Min(1)
	@Max(8)
	private int totalCourses;

	private boolean completed;

	private int tEnterMain;

	@NotEmpty
	private List<@Valid SingleGameDetailRequest> details;
}
