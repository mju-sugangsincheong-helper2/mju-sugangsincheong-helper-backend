package com.mjusugangsincheonghelper.member.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
public class PrivacyAgreementResponse {

	private final Long memberId;
	private final boolean privacyPolicyAgreed;
	private final Instant agreedAt;
}
