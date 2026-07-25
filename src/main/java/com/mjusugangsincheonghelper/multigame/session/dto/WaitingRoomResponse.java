package com.mjusugangsincheonghelper.multigame.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitingRoomResponse {

	private String multigameId;
	private String state;
	private int participation;
}
