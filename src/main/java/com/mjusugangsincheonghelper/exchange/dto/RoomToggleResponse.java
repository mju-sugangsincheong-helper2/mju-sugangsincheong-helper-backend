package com.mjusugangsincheonghelper.exchange.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RoomToggleResponse {

	private Long roomId;
	private boolean isOn;
}
