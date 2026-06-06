package com.mjusugangsincheonghelper.auth.dto;

import com.mjusugangsincheonghelper.auth.authorization.consent.MemberAgreementService.ConsentStatus;
import com.mjusugangsincheonghelper.database.entity.Member;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MemberMeResponse {

	private final Long memberId;
	private final String role;
	private final String name;
	private final String position;
	private final String department;
	private final boolean isPrivacyPolicyAgreed;
	private final Instant privacyPolicyAgreedAt;
	private final Instant createdAt;

	public static MemberMeResponse from(Member member, ConsentStatus consentStatus) {
		return MemberMeResponse.builder()
				.memberId(member.getId())
				.role(member.getRole().name())
				.name(member.getName())
				.position(member.getPosition())
				.department(member.getDepartment())
				.isPrivacyPolicyAgreed(consentStatus.status())
				.privacyPolicyAgreedAt(consentStatus.agreedAt())
				.createdAt(member.getCreatedAt())
				.build();
	}
}
