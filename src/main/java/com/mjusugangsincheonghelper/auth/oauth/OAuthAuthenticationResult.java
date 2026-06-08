package com.mjusugangsincheonghelper.auth.oauth;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OAuthAuthenticationResult {

	private final AuthenticatedIdentity identity;
	private final boolean newUser;
}
