package com.mjusugangsincheonghelper.notification.consumer.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationConsumerService {

	private static final int MAX_BATCH_SIZE = 400;

	public void processNotificationEvents(List<NotificationEventMessage> events) {
		if (events == null || events.isEmpty()) {
			return;
		}

		for (int i = 0; i < events.size(); i += MAX_BATCH_SIZE) {
			List<NotificationEventMessage> batch = events.subList(i, Math.min(i + MAX_BATCH_SIZE, events.size()));
			sendFirebaseCloudMessagingBatch(batch);
		}
	}

	@SuppressWarnings("deprecation")
	private void sendFirebaseCloudMessagingBatch(List<NotificationEventMessage> batch) {
		if (FirebaseApp.getApps().isEmpty()) {
			log.warn("FirebaseApp is not initialized. Skipping Firebase Cloud Messaging send. batchSize={}", batch.size());
			return;
		}

		List<Message> firebaseCloudMessagingMessages = new ArrayList<>();
		for (NotificationEventMessage event : batch) {
			if (event.getToken() == null || event.getToken().isBlank()) {
				log.debug("Firebase Cloud Messaging token is empty in notification event message. Skipping Firebase Cloud Messaging send.");
				continue;
			}

			Message.Builder builder = Message.builder()
					.setToken(event.getToken())
					.setApnsConfig(ApnsConfig.builder()
							.setAps(Aps.builder().setSound("default").build())
							.build())
					.setAndroidConfig(AndroidConfig.builder()
							.setNotification(AndroidNotification.builder().setSound("default").build())
							.build());

			if (event.getNotification() != null) {
				builder.setNotification(Notification.builder()
						.setTitle(event.getNotification().getTitle())
						.setBody(event.getNotification().getBody())
						.build());
			}

			if (event.getData() != null && !event.getData().isEmpty()) {
				builder.putAllData(event.getData());
			}

			firebaseCloudMessagingMessages.add(builder.build());
		}

		if (firebaseCloudMessagingMessages.isEmpty()) {
			return;
		}

		try {
			log.info("Sending Firebase Cloud Messaging batch messages. count={}", firebaseCloudMessagingMessages.size());
			FirebaseMessaging.getInstance().sendEach(firebaseCloudMessagingMessages);
		} catch (Exception e) {
			log.error("Failed to send Firebase Cloud Messaging batch. count={}", firebaseCloudMessagingMessages.size(), e);
			throw new BaseException(ErrorCode.NOTIFICATION_SEND_FAILED, e);
		}
	}
}
