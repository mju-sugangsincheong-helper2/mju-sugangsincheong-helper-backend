package com.mjusugangsincheonghelper.auth.oauth;

import com.mjusugangsincheonghelper.auth.authentication.identity.AuthenticatedIdentity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OAuthAuthenticationResult {

	private final AuthenticatedIdentity identity;
	private final boolean newUser;
}
