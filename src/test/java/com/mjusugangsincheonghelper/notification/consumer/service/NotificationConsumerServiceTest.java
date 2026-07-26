package com.mjusugangsincheonghelper.notification.consumer.service;

import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NotificationConsumerServiceTest {

	private final NotificationConsumerService notificationConsumerService = new NotificationConsumerService();

	@Test
	@DisplayName("400개 초과 메시지 리스트도 400개 단위 분할하여 예외 없이 처리한다 (FirebaseApp 미초기화 상태 안전 처리)")
	void shouldProcessEventsInBatchesOf400WithoutException() {
		List<NotificationEventMessage> events = new ArrayList<>();
		for (int i = 0; i < 950; i++) {
			events.add(NotificationEventMessage.builder()
					.token("token-" + i)
					.notification(NotificationEventMessage.NotificationPayload.builder()
							.title("제목 " + i)
							.body("내용 " + i)
							.build())
					.data(Map.of("type", "SYSTEM_NOTICE", "urgency", "NORMAL"))
					.build());
		}

		assertDoesNotThrow(() -> notificationConsumerService.processNotificationEvents(events));
	}
}
