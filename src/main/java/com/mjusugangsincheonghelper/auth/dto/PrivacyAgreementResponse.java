package com.mjusugangsincheonghelper.auth.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyAgreementResponse {

	private Long memberId;
	private boolean privacyPolicyAgreed;
	private Instant agreedAt;
}
