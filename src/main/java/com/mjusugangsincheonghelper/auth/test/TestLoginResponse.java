package com.mjusugangsincheonghelper.auth.test;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestLoginResponse {

	private Long memberId;
	private String role;
	private String name;
	private String position;
	private String department;
	private String sessionAccessToken;
	private String sessionRefreshToken;
}
