package com.mjusugangsincheonghelper.notification.dto;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTokenResponse {

	private String fcmToken;
	private Instant updatedAt;

	public static NotificationTokenResponse from(MemberDevice device) {
		return NotificationTokenResponse.builder()
				.fcmToken(device.getFcmToken())
				.updatedAt(device.getUpdatedAt() != null ? device.getUpdatedAt() : Instant.now())
				.build();
	}

	public static NotificationTokenResponse of(String fcmToken) {
		return NotificationTokenResponse.builder()
				.fcmToken(fcmToken)
				.updatedAt(Instant.now())
				.build();
	}
}
