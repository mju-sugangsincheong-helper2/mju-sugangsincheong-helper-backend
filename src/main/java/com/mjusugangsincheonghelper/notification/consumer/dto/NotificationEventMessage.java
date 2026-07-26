package com.mjusugangsincheonghelper.notification.consumer.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEventMessage {

	private String token;
	private NotificationPayload notification;
	private Map<String, String> data;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class NotificationPayload {
		private String title;
		private String body;
	}
}
