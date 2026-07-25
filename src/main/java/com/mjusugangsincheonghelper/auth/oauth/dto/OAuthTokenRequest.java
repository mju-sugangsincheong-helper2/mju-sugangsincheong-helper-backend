package com.mjusugangsincheonghelper.auth.oauth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthTokenRequest {

	@NotBlank(message = "code는 필수입니다.")
	private String code;

	@NotBlank(message = "state는 필수입니다.")
	private String state;

	private String accessToken;
}
