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

	@Column(nullable = false, unique = true, length = 512)
	private String refreshToken;

	@Column(name = "fcm_token", length = 512)
	private String fcmToken;

	@Column(name = "platformjs_name", length = 100)
	private String platformjsName;

	@Column(name = "platformjs_version", length = 50)
	private String platformjsVersion;

	@Column(name = "platformjs_layout", length = 50)
	private String platformjsLayout;

	@Column(name = "platformjs_prerelease", length = 50)
	private String platformjsPrerelease;

	@Column(name = "platformjs_os", length = 100)
	private String platformjsOs;

	@Column(name = "platformjs_manufacturer", length = 100)
	private String platformjsManufacturer;

	@Column(name = "platformjs_product", length = 100)
	private String platformjsProduct;

	@Column(name = "platformjs_description", columnDefinition = "TEXT")
	private String platformjsDescription;

	@Column(name = "platformjs_ua", columnDefinition = "TEXT")
	private String platformjsUa;

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
	public MemberDevice(Long memberId, String refreshToken,
			String platformjsName, String platformjsVersion,
			String platformjsLayout, String platformjsPrerelease,
			String platformjsOs, String platformjsManufacturer,
			String platformjsProduct, String platformjsDescription,
			String platformjsUa, Instant expiresAt) {
		this.memberId = memberId;
		this.refreshToken = refreshToken;
		this.platformjsName = platformjsName;
		this.platformjsVersion = platformjsVersion;
		this.platformjsLayout = platformjsLayout;
		this.platformjsPrerelease = platformjsPrerelease;
		this.platformjsOs = platformjsOs;
		this.platformjsManufacturer = platformjsManufacturer;
		this.platformjsProduct = platformjsProduct;
		this.platformjsDescription = platformjsDescription;
		this.platformjsUa = platformjsUa;
		this.lastAccessedAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	public void updateRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
		this.lastAccessedAt = Instant.now();
	}

	public void updateAccessInfo(String refreshToken,
			String platformjsName, String platformjsVersion,
			String platformjsLayout, String platformjsPrerelease,
			String platformjsOs, String platformjsManufacturer,
			String platformjsProduct, String platformjsDescription,
			String platformjsUa) {
		this.refreshToken = refreshToken;
		this.platformjsName = platformjsName;
		this.platformjsVersion = platformjsVersion;
		this.platformjsLayout = platformjsLayout;
		this.platformjsPrerelease = platformjsPrerelease;
		this.platformjsOs = platformjsOs;
		this.platformjsManufacturer = platformjsManufacturer;
		this.platformjsProduct = platformjsProduct;
		this.platformjsDescription = platformjsDescription;
		this.platformjsUa = platformjsUa;
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
