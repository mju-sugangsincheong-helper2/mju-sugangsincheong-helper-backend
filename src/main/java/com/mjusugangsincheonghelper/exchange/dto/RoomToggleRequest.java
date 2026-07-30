package com.mjusugangsincheonghelper.exchange.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomToggleRequest {

	@JsonProperty("isOn")
	// @JsonAlias({"on", "isOn", "is_on"})
	private boolean isOn;

	@JsonProperty("isOn")
	public boolean isOn() {
		return isOn;
	}
}
