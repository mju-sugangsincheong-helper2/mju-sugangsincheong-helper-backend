package com.mjusugangsincheonghelper.auth.oauth;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OAuthAuthenticationResult {

	private final AuthenticatedIdentity identity;
	private final boolean newUser;
	private final boolean mergeRequired;
	private final String mergeTicket;
	private final String googleSubId;

	public static OAuthAuthenticationResult success(AuthenticatedIdentity identity, boolean newUser) {
		return OAuthAuthenticationResult.builder()
				.identity(identity)
				.newUser(newUser)
				.mergeRequired(false)
				.build();
	}

	public static OAuthAuthenticationResult mergeRequired(String mergeTicket, String googleSubId) {
		return OAuthAuthenticationResult.builder()
				.mergeRequired(true)
				.mergeTicket(mergeTicket)
				.googleSubId(googleSubId)
				.build();
	}
}
