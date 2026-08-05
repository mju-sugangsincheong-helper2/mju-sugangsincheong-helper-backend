package com.mjusugangsincheonghelper.multigame.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameApplyResponse {
	private String status;
	private String currentState;
	private Long seq;
	private Long limit;
	private Long rank;
	private Integer subjectId;
	private Integer remaining;
}
