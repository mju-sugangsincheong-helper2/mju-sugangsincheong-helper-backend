package com.mjusugangsincheonghelper.exchange.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomToggleResponse {

	private Long roomId;

	@JsonProperty("isOn")
	private boolean isOn;

	@JsonProperty("isOn")
	public boolean isOn() {
		return isOn;
	}
}
