package com.mjusugangsincheonghelper.auth.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthenticatedIdentity {

	private final Long memberId;
}
