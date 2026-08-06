package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "member_device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MemberDevice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
	private String refreshTokenHash;

	@Column(name = "fcm_token", length = 512)
	private String fcmToken;

	@Column(name = "platformjs_name", length = 100)
	private String platformJsName;

	@Column(name = "platformjs_version", length = 50)
	private String platformJsVersion;

	@Column(name = "platformjs_layout", length = 50)
	private String platformJsLayout;

	@Column(name = "platformjs_prerelease", length = 50)
	private String platformJsPrerelease;

	@Column(name = "platformjs_os", length = 100)
	private String platformJsOs;

	@Column(name = "platformjs_manufacturer", length = 100)
	private String platformJsManufacturer;

	@Column(name = "platformjs_product", length = 100)
	private String platformJsProduct;

	@Column(name = "platformjs_description", columnDefinition = "TEXT")
	private String platformJsDescription;

	@Column(name = "platformjs_ua", columnDefinition = "TEXT")
	private String platformJsUa;

	@Column(name = "last_accessed_at")
	private Instant lastAccessedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	public MemberDevice(Long memberId, String refreshTokenHash,
			String platformJsName, String platformJsVersion,
			String platformJsLayout, String platformJsPrerelease,
			String platformJsOs, String platformJsManufacturer,
			String platformJsProduct, String platformJsDescription,
			String platformJsUa, Instant expiresAt) {
		this.memberId = memberId;
		this.refreshTokenHash = refreshTokenHash;
		this.platformJsName = platformJsName;
		this.platformJsVersion = platformJsVersion;
		this.platformJsLayout = platformJsLayout;
		this.platformJsPrerelease = platformJsPrerelease;
		this.platformJsOs = platformJsOs;
		this.platformJsManufacturer = platformJsManufacturer;
		this.platformJsProduct = platformJsProduct;
		this.platformJsDescription = platformJsDescription;
		this.platformJsUa = platformJsUa;
		this.lastAccessedAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	public void updateRefreshTokenHash(String refreshTokenHash) {
		this.refreshTokenHash = refreshTokenHash;
		this.lastAccessedAt = Instant.now();
	}

	public void updateAccessInfo(String refreshTokenHash,
			String platformJsName, String platformJsVersion,
			String platformJsLayout, String platformJsPrerelease,
			String platformJsOs, String platformJsManufacturer,
			String platformJsProduct, String platformJsDescription,
			String platformJsUa) {
		this.refreshTokenHash = refreshTokenHash;
		this.platformJsName = platformJsName;
		this.platformJsVersion = platformJsVersion;
		this.platformJsLayout = platformJsLayout;
		this.platformJsPrerelease = platformJsPrerelease;
		this.platformJsOs = platformJsOs;
		this.platformJsManufacturer = platformJsManufacturer;
		this.platformJsProduct = platformJsProduct;
		this.platformJsDescription = platformJsDescription;
		this.platformJsUa = platformJsUa;
		this.lastAccessedAt = Instant.now();
	}

	public void switchMember(Long newMemberId) {
		this.memberId = newMemberId;
	}

	public void updateFcmToken(String fcmToken) {
		this.fcmToken = fcmToken;
		this.lastAccessedAt = Instant.now();
	}

	public void clearFcmToken() {
		this.fcmToken = null;
		this.lastAccessedAt = Instant.now();
	}
}
