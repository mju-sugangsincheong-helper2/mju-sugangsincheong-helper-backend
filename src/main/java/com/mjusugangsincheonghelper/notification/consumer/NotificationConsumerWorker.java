package com.mjusugangsincheonghelper.notification.consumer;

import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.global.config.PgmqMessageDto;
import com.mjusugangsincheonghelper.global.config.PgmqProperties;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import com.mjusugangsincheonghelper.notification.consumer.service.NotificationConsumerService;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumerWorker {

	public static final String QUEUE_NAME = "notification_queue";

	private final PgmqService pgmqService;
	private final NotificationConsumerService notificationConsumerService;
	private final ObjectMapper objectMapper;
	private final PgmqProperties pgmqProperties;

	@PostConstruct
	public void createQueue() {
		try {
			pgmqService.createQueue(QUEUE_NAME);
		} catch (Exception e) {
			log.debug("Queue may already exist: {}", e.getMessage());
		}
	}

	@Scheduled(fixedDelayString = "${app.pgmq.notification.poll-interval:1s}", scheduler = "pgmqScheduler")
	void poll() {
		PgmqProperties.WorkerConfig config = pgmqProperties.getNotification();
		List<PgmqMessageDto> messages = pgmqService.read(QUEUE_NAME, config.getVisibilityTimeout(), config.getBatchSize());
		if (messages.isEmpty()) {
			return;
		}

		List<NotificationEventMessage> validEvents = new ArrayList<>();
		List<Long> messageIdsToDelete = new ArrayList<>();

		for (PgmqMessageDto msg : messages) {
			try {
				if (msg.getReadCt() > config.getMaxRetryCount()) {
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
