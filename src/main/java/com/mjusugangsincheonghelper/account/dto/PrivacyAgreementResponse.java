package com.mjusugangsincheonghelper.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PrivacyAgreementResponse {

	private final Long memberId;
	private final boolean privacyPolicyAgreed;
	private final Long agreedAt;
}
