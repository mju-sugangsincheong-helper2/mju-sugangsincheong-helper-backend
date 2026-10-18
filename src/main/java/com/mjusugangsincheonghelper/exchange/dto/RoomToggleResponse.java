package com.mjusugangsincheonghelper.exchange.dto;

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

	private boolean on;
}
