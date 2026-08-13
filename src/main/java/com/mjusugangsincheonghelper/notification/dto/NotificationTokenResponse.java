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

	private String firebaseCloudMessagingRegistrationToken;
	private Instant updatedAt;

	public static NotificationTokenResponse from(MemberDevice device) {
		return NotificationTokenResponse.builder()
				.firebaseCloudMessagingRegistrationToken(device.getFirebaseCloudMessagingRegistrationToken())
				.updatedAt(device.getUpdatedAt() != null ? device.getUpdatedAt() : Instant.now())
				.build();
	}

	public static NotificationTokenResponse of(String firebaseCloudMessagingRegistrationToken) {
		return NotificationTokenResponse.builder()
				.firebaseCloudMessagingRegistrationToken(firebaseCloudMessagingRegistrationToken)
				.updatedAt(Instant.now())
				.build();
	}
}
