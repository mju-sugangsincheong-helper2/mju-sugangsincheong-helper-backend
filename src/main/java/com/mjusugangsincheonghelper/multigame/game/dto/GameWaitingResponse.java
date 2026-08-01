package com.mjusugangsincheonghelper.multigame.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameWaitingResponse {
	private String multigameId;
	private String state;
	private long participation;
}
