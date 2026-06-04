package com.mjusugangsincheonghelper.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MergeResponse {

	private final Long memberId;
	private final String role;
	private final String name;
	private final String position;
	private final String department;
}
