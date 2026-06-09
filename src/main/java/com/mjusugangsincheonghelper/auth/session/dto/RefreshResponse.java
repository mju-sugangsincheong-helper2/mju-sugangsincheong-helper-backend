package com.mjusugangsincheonghelper.auth.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RefreshResponse {

	private final String status;
	private final String role;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String accessToken;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String refreshToken;
}
