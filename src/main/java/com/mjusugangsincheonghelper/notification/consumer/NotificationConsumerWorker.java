package com.mjusugangsincheonghelper.notification.consumer;

import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.global.config.PgmqMessageDto;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import com.mjusugangsincheonghelper.notification.consumer.service.NotificationConsumerService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationConsumerWorker {

	public static final String QUEUE_NAME = "notification_queue";
	private static final int VISIBILITY_TIMEOUT = 30;
	private static final int BATCH_SIZE = 400;
	private static final int MAX_RETRY_COUNT = 5;

	private final PgmqService pgmqService;
	private final NotificationConsumerService notificationConsumerService;
	private final ObjectMapper objectMapper;
	private final TaskScheduler pgmqScheduler;

	public NotificationConsumerWorker(
			PgmqService pgmqService,
			NotificationConsumerService notificationConsumerService,
			ObjectMapper objectMapper,
			@Qualifier("pgmqScheduler") TaskScheduler pgmqScheduler) {
		this.pgmqService = pgmqService;
		this.notificationConsumerService = notificationConsumerService;
		this.objectMapper = objectMapper;
		this.pgmqScheduler = pgmqScheduler;
	}

	@PostConstruct
	public void start() {
		try {
			pgmqService.createQueue(QUEUE_NAME);
		} catch (Exception e) {
			log.debug("Queue may already exist: {}", e.getMessage());
		}
		pgmqScheduler.scheduleWithFixedDelay(this::poll, Duration.ofSeconds(1));
	}

	private void poll() {
		List<PgmqMessageDto> messages = pgmqService.read(QUEUE_NAME, VISIBILITY_TIMEOUT, BATCH_SIZE);
		if (messages.isEmpty()) {
			return;
		}

		List<NotificationEventMessage> validEvents = new ArrayList<>();
		List<Long> messageIdsToDelete = new ArrayList<>();

		for (PgmqMessageDto msg : messages) {
			try {
				if (msg.getReadCt() > MAX_RETRY_COUNT) {
					log.error("Notification message exceeded max retries. Archiving message: msgId={}, readCt={}", msg.getMsgId(), msg.getReadCt());
					pgmqService.archive(QUEUE_NAME, msg.getMsgId());
					continue;
				}

				NotificationEventMessage event = objectMapper.readValue(msg.getMessage(), NotificationEventMessage.class);
				validEvents.add(event);
				messageIdsToDelete.add(msg.getMsgId());
			} catch (Exception e) {
				log.error("Failed to deserialize notification message: msgId={}, readCt={}", msg.getMsgId(), msg.getReadCt(), e);
			}
		}

		if (!validEvents.isEmpty()) {
			try {
				notificationConsumerService.processNotificationEvents(validEvents);
				for (Long msgId : messageIdsToDelete) {
					pgmqService.delete(QUEUE_NAME, msgId);
				}
			} catch (Exception e) {
				log.error("Failed to process notification batch: count={}", validEvents.size(), e);
			}
		}
	}
}
