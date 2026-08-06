package com.mjusugangsincheonghelper.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PGMQ 비동기 워커 설정 (app.pgmq.*).
 *
 * <p>notification: 알림 전송 소비자, cycle-detection: 교환 사이클 탐지 워커.
 * 배치 크기/재시도 상한 및 큐 이름은 yml에서 관리한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.pgmq")
public class PgmqProperties {

	/** 아카이브 메시지 삭제 전 보관 기간 (retention period). */
	private Duration archiveRetentionPeriod = Duration.ofDays(7);

	/** 아카이브 청소 크론 표현식. */
	private String archiveCleanupCron = "0 30 3 * * *";

	/** 알림 전송 큐 소비자 설정. */
	private WorkerConfig notification = new WorkerConfig("notification_queue", 400);

	/** 교환 사이클 탐지 워커 설정. */
	private WorkerConfig cycleDetection = new WorkerConfig("exchange_cycle_detection", 1);


	@Getter
	@Setter
	public static class WorkerConfig {

		/** PGMQ 큐 이름. */
		private String queueName;

		/** PGMQ visibility timeout (초). 메시지가 재노출되기까지의 잠금 시간. */
		private int visibilityTimeout = 30;

		/** 1회 폴링 시 읽어오는 최대 메시지 수. */
		private int batchSize;

		/** 읽기 횟수(read_ct)가 이 값을 초과하면 아카이브 처리한다. */
		private int maxRetryCount = 5;

		public WorkerConfig() {
		}

		public WorkerConfig(String queueName, int batchSize) {
			this.queueName = queueName;
			this.batchSize = batchSize;
		}
	}
}
