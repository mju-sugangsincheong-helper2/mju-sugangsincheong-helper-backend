package com.mjusugangsincheonghelper.exchange.service;

import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.exchange.dto.CycleDetectionMessage;
import com.mjusugangsincheonghelper.global.config.PgmqMessageDto;
import com.mjusugangsincheonghelper.global.config.PgmqProperties;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeCycleDetectionWorker {

	private final PgmqService pgmqService;
	private final ExchangeCycleDetector cycleDetector;
	private final ObjectMapper objectMapper;
	private final PgmqProperties pgmqProperties;

	@PostConstruct
	public void createQueue() {
		try {
			pgmqService.createQueue(pgmqProperties.getCycleDetection().getQueueName());
		} catch (Exception e) {
			log.debug("Queue may already exist: {}", e.getMessage());
		}
	}

	@Scheduled(fixedDelayString = "${app.pgmq.cycle-detection.poll-interval:1s}", scheduler = "pgmqScheduler")
	void poll() {
		PgmqProperties.WorkerConfig config = pgmqProperties.getCycleDetection();
		String queueName = config.getQueueName();
		List<PgmqMessageDto> messages = pgmqService.read(queueName, config.getVisibilityTimeout(), config.getBatchSize());

		for (PgmqMessageDto msg : messages) {
			try {
				if (msg.getReadCt() > config.getMaxRetryCount()) {
					log.error("Exchange cycle detection message exceeded max retries. Archiving message: msgId={}, readCt={}", msg.getMsgId(), msg.getReadCt());
					pgmqService.archive(queueName, msg.getMsgId());
					continue;
				}

				CycleDetectionMessage detectionMsg = objectMapper.readValue(msg.getMessage(), CycleDetectionMessage.class);
				cycleDetector.detectCyclesAndCreateRooms(
						detectionMsg.getTerm(),
						detectionMsg.getIntentId(),
						detectionMsg.getMemberId(),
						detectionMsg.getGiveCourseNo(),
						detectionMsg.getWantCourseNo()
				);
				pgmqService.delete(queueName, msg.getMsgId());
			} catch (Exception e) {
				log.error("Failed to process cycle detection message: msgId={}, readCt={}", msg.getMsgId(), msg.getReadCt(), e);
			}
		}
	}
}
