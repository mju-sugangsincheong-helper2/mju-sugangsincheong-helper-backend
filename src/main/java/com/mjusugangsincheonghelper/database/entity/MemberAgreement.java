package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAgreement {

	@Id
	private Long memberId;

	@Column(nullable = false)
	private boolean status;

	@Column(name = "agreed_at")
	private Long agreedAt;

	@Builder
	public MemberAgreement(Long memberId) {
		this.memberId = memberId;
		this.status = false;
		this.agreedAt = null;
	}

	public static MemberAgreement agree(Long memberId) {
		MemberAgreement agreement = new MemberAgreement(memberId);
		agreement.status = true;
		agreement.agreedAt = System.currentTimeMillis();
		return agreement;
	}

	public void agree() {
		this.status = true;
		this.agreedAt = System.currentTimeMillis();
	}
}
