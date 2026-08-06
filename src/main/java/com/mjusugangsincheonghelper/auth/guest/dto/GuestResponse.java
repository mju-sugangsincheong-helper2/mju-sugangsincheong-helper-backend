package com.mjusugangsincheonghelper.auth.guest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class GuestResponse {

	private final Long memberId;
	private final String role;
	private final String name;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String accessToken;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String refreshToken;

	public static GuestResponse of(Long memberId, String role, String name, String accessToken, String refreshToken) {
		return GuestResponse.builder()
				.memberId(memberId)
				.role(role)
				.name(name)
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.build();
	}
}
