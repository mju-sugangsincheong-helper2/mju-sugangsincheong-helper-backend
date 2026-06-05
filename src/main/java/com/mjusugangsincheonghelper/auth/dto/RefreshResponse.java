package com.mjusugangsincheonghelper.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponse {

	private String status;
	private String role;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String accessToken;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String refreshToken;
}
