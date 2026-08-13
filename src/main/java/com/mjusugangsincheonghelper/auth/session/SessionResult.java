package com.mjusugangsincheonghelper.auth.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SessionResult {

	private final String sessionAccessToken;
	private final String sessionRefreshToken;
	private final Long memberId;
	private final String role;
	private final String name;
	private final String position;
	private final String department;
}
