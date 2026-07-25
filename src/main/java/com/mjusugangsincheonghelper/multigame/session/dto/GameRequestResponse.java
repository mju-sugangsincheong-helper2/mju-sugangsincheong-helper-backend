package com.mjusugangsincheonghelper.multigame.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRequestResponse {

	private String status;
	private Integer seq;
	private Integer limit;
	private Integer subjectId;
	private Integer remaining;
	private String currentState;
}
