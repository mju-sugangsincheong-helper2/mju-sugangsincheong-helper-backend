package com.mjusugangsincheonghelper.account.dto;

import com.mjusugangsincheonghelper.auth.session.token.RefreshTokenHasher;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccountDeviceResponse {

	private Long id;
	private String platformOs;
	private String platformName;
	private String platformVersion;
	private String platformProduct;
	private String description;
	private boolean pushNotificationEnabled;
	private boolean isCurrentDevice;
	private Instant lastAccessedAt;
	private Instant createdAt;

	public static AccountDeviceResponse from(MemberDevice device, String currentRefreshToken) {
		boolean pushEnabled = device.getFirebaseCloudMessagingRegistrationToken() != null && !device.getFirebaseCloudMessagingRegistrationToken().isBlank();
		boolean current = currentRefreshToken != null
				&& device.getRefreshTokenHash().equals(RefreshTokenHasher.hash(currentRefreshToken));

		return AccountDeviceResponse.builder()
				.id(device.getId())
				.platformOs(device.getPlatformJsOs())
				.platformName(device.getPlatformJsName())
				.platformVersion(device.getPlatformJsVersion())
				.platformProduct(device.getPlatformJsProduct())
				.description(device.getPlatformJsDescription())
				.pushNotificationEnabled(pushEnabled)
				.isCurrentDevice(current)
				.lastAccessedAt(device.getLastAccessedAt())
				.createdAt(device.getCreatedAt())
				.build();
	}
}
