package com.mjusugangsincheonghelper.account.dto;

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
		boolean pushEnabled = device.getFcmToken() != null && !device.getFcmToken().isBlank();
		boolean current = currentRefreshToken != null && currentRefreshToken.equals(device.getRefreshToken());

		return AccountDeviceResponse.builder()
				.id(device.getId())
				.platformOs(device.getPlatformjsOs())
				.platformName(device.getPlatformjsName())
				.platformVersion(device.getPlatformjsVersion())
				.platformProduct(device.getPlatformjsProduct())
				.description(device.getPlatformjsDescription())
				.pushNotificationEnabled(pushEnabled)
				.isCurrentDevice(current)
				.lastAccessedAt(device.getLastAccessedAt())
				.createdAt(device.getCreatedAt())
				.build();
	}
}
