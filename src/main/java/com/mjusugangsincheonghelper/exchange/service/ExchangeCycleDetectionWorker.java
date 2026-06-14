package com.mjusugangsincheonghelper.exchange.service;

import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.exchange.dto.CycleDetectionMessage;
import com.mjusugangsincheonghelper.global.config.PgmqMessageDto;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeCycleDetectionWorker {

	private static final int VISIBILITY_TIMEOUT = 30;
	private static final int BATCH_SIZE = 1;

	private final PgmqService pgmqService;
	private final ExchangeCycleDetector cycleDetector;
	private final ObjectMapper objectMapper;

	@Qualifier("pgmqScheduler")
	private final TaskScheduler pgmqScheduler;

	@PostConstruct
	public void start() {
		try {
			pgmqService.createQueue(ExchangeCycleDetector.QUEUE_NAME);
		} catch (Exception e) {
			log.debug("Queue may already exist: {}", e.getMessage());
		}
		pgmqScheduler.scheduleWithFixedDelay(this::poll, Duration.ofSeconds(1));
	}

	private void poll() {
		List<PgmqMessageDto> messages = pgmqService.read(ExchangeCycleDetector.QUEUE_NAME, VISIBILITY_TIMEOUT, BATCH_SIZE);

		for (PgmqMessageDto msg : messages) {
			try {
				CycleDetectionMessage detectionMsg = objectMapper.readValue(msg.getMessage(), CycleDetectionMessage.class);
				cycleDetector.detectCyclesAndCreateRooms(
						detectionMsg.getTerm(),
						detectionMsg.getIntentId(),
						detectionMsg.getMemberId(),
						detectionMsg.getGiveCourseNo(),
						detectionMsg.getWantCourseNo()
				);
				pgmqService.delete(ExchangeCycleDetector.QUEUE_NAME, msg.getMsgId());
			} catch (Exception e) {
				log.error("Failed to process cycle detection message: msgId={}", msg.getMsgId(), e);
			}
		}
	}
}
