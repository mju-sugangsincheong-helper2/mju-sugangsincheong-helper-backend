package com.mjusugangsincheonghelper.notification.consumer;

import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.global.config.PgmqMessageDto;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import com.mjusugangsincheonghelper.notification.consumer.service.NotificationConsumerService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerWorkerTest {

	@Mock
	private PgmqService pgmqService;

	@Mock
	private NotificationConsumerService notificationConsumerService;

	@Mock
	private TaskScheduler pgmqScheduler;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private NotificationConsumerWorker worker;

	@BeforeEach
	void setUp() {
		worker = new NotificationConsumerWorker(pgmqService, notificationConsumerService, objectMapper, pgmqScheduler);
	}

	@Test
	@DisplayName("큐에서 읽은 메시지를 정상적으로 역직렬화하고 FCM 서비스를 호출한 뒤 삭제한다")
	void shouldPollAndProcessMessages() throws Exception {
		NotificationEventMessage messagePayload = NotificationEventMessage.builder()
				.token("test-token-1")
				.notification(NotificationEventMessage.NotificationPayload.builder()
						.title("테스트 제목")
						.body("테스트 내용")
						.build())
				.data(Map.of("type", "SYSTEM_NOTICE"))
				.build();

		String jsonMessage = objectMapper.writeValueAsString(messagePayload);
		PgmqMessageDto messageDto = PgmqMessageDto.builder()
				.msgId(100L)
				.readCt(1)
				.message(jsonMessage)
				.build();

		given(pgmqService.read(eq(NotificationConsumerWorker.QUEUE_NAME), eq(30), eq(400)))
				.willReturn(List.of(messageDto));
		doNothing().when(notificationConsumerService).processNotificationEvents(any());

		Method pollMethod = NotificationConsumerWorker.class.getDeclaredMethod("poll");
		pollMethod.setAccessible(true);
		pollMethod.invoke(worker);

		verify(notificationConsumerService).processNotificationEvents(any());
		verify(pgmqService).delete(eq(NotificationConsumerWorker.QUEUE_NAME), eq(100L));
	}

	@Test
	@DisplayName("재시도 횟수(MAX_RETRY_COUNT)를 초과한 메시지는 아카이브 처리한다")
	void shouldArchiveExceededRetryMessages() throws Exception {
		PgmqMessageDto poisonMessage = PgmqMessageDto.builder()
				.msgId(200L)
				.readCt(6)
				.message("{\"invalid\":\"json\"}")
				.build();

		given(pgmqService.read(eq(NotificationConsumerWorker.QUEUE_NAME), eq(30), eq(400)))
				.willReturn(List.of(poisonMessage));

		Method pollMethod = NotificationConsumerWorker.class.getDeclaredMethod("poll");
		pollMethod.setAccessible(true);
		pollMethod.invoke(worker);

		verify(pgmqService).archive(eq(NotificationConsumerWorker.QUEUE_NAME), eq(200L));
	}
}
