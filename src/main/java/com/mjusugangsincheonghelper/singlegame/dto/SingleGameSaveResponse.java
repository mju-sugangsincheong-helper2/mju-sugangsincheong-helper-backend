package com.mjusugangsincheonghelper.singlegame.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SingleGameSaveResponse {

	private Long gameId;
	private String message;
}
