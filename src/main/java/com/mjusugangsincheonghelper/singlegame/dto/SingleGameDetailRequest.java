package com.mjusugangsincheonghelper.singlegame.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class SingleGameDetailRequest {

	@Min(1)
	private int sequence;

	@JsonProperty("tClickCourse")
	private int tClickCourse;

	@JsonProperty("tClickYes")
	private int tClickYes;

	@JsonProperty("tClickOk")
	private int tClickOk;
}
