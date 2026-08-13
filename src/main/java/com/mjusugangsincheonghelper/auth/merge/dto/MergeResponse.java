package com.mjusugangsincheonghelper.auth.merge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
	private final Boolean newUser;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String sessionAccessToken;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String sessionRefreshToken;

	public static MergeResponse of(Long memberId, String role, String name, String position, String department, Boolean newUser, String sessionAccessToken, String sessionRefreshToken) {
		return MergeResponse.builder()
				.memberId(memberId)
				.role(role)
				.name(name)
				.position(position)
				.department(department)
				.newUser(newUser)
				.sessionAccessToken(sessionAccessToken)
				.sessionRefreshToken(sessionRefreshToken)
				.build();
	}
}
