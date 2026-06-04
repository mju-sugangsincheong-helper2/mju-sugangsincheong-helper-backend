package com.mjusugangsincheonghelper.auth.dto;

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
}
