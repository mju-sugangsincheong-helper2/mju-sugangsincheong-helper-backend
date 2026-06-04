package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Member {

	public enum Role {
		GUEST, MEMBER, ADMIN
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(length = 50)
	private String position;

	@Column(length = 50)
	private String department;

	@Column(length = 50)
	private String name;

	@Column(name = "is_privacy_policy_agreed", nullable = false)
	private boolean privacyPolicyAgreed;

	@Column(name = "privacy_policy_agreed_at")
	private Instant privacyPolicyAgreedAt;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	public Member(Role role, String position, String department, String name, boolean privacyPolicyAgreed) {
		this.role = role;
		this.position = position;
		this.department = department;
		this.name = name;
		this.privacyPolicyAgreed = privacyPolicyAgreed;
	}

	public void promoteToMember(String name, String position, String department) {
		this.role = Role.MEMBER;
		this.name = name;
		this.position = position;
		this.department = department;
		this.privacyPolicyAgreed = true;
		this.privacyPolicyAgreedAt = Instant.now();
	}

	public void agreeToPrivacyPolicy() {
		this.privacyPolicyAgreed = true;
		this.privacyPolicyAgreedAt = Instant.now();
	}
}
