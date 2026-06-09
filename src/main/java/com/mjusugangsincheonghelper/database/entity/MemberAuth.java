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
@Table(name = "member_auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MemberAuth {

	public enum AuthType {
		GUEST_KEY, GOOGLE, TEST
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private Long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthType authType;

	@Column(nullable = false, unique = true, length = 255)
	private String authKey;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	public MemberAuth(Long memberId, AuthType authType, String authKey) {
		this.memberId = memberId;
		this.authType = authType;
		this.authKey = authKey;
	}

	public void switchAuthKey(AuthType authType, String authKey) {
		this.authType = authType;
		this.authKey = authKey;
		this.lastLoginAt = Instant.now();
	}

	public void updateLastLoginAt() {
		this.lastLoginAt = Instant.now();
	}
}
