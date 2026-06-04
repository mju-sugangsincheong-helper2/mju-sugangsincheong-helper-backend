package com.mjusugangsincheonghelper.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RefreshResponse {

	private final String status;
	private final String role;
}
