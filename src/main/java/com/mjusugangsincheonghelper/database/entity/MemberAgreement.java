package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "member_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAgreement {

	@Id
	private Long memberId;

	@Column(nullable = false)
	private boolean status;

	@Column(name = "agreed_at", nullable = false)
	private Instant agreedAt;

	@Builder
	public MemberAgreement(Long memberId) {
		this.memberId = memberId;
		this.status = false;
		this.agreedAt = null;
	}

	public static MemberAgreement agree(Long memberId) {
		MemberAgreement agreement = new MemberAgreement(memberId);
		agreement.status = true;
		agreement.agreedAt = Instant.now();
		return agreement;
	}

	public void agree() {
		this.status = true;
		this.agreedAt = Instant.now();
	}
}
