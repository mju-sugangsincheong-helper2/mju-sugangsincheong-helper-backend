package com.mjusugangsincheonghelper.auth.merge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
public class MergeResponse {

	private final Long memberId;
	private final String role;
	private final String name;
	private final String position;
	private final String department;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String accessToken;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String refreshToken;
}
