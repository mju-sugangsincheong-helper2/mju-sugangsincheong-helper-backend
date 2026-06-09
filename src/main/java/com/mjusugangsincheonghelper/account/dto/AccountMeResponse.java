package com.mjusugangsincheonghelper.account.dto;

import com.mjusugangsincheonghelper.database.entity.Member;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AccountMeResponse {

	private final Long memberId;
	private final String role;
	private final String name;
	private final String position;
	private final String department;
	private final boolean isPrivacyPolicyAgreed;
	private final Instant createdAt;

	public static AccountMeResponse from(Member member, boolean privacyPolicyAgreed) {
		return AccountMeResponse.builder()
				.memberId(member.getId())
				.role(member.getRole().name())
				.name(member.getName())
				.position(member.getPosition())
				.department(member.getDepartment())
				.isPrivacyPolicyAgreed(privacyPolicyAgreed)
				.createdAt(member.getCreatedAt())
				.build();
	}
}
