package com.mjusugangsincheonghelper.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * PGMQ 비동기 워커 설정 (app.pgmq.*).
 *
 * <p>notification: 알림 전송 소비자, cycle-detection: 교환 사이클 탐지 워커.
 * 배치 크기/재시도 상한은 운영 부하 튜닝 대상이므로 yml에서 관리한다.
 * 프로필별로 다르게 두려면 해당 프로필 yml에서 app.pgmq.* 을 오버라이드하면 된다.
 *
 * <p>폴링 주기(app.pgmq.*.poll-interval)는 워커의 {@code @Scheduled(fixedDelayString)}
 * placeholder가 동일한 yml 키를 직접 참조하므로 여기서는 다루지 않는다.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.pgmq")
public class PgmqProperties {

	/** 알림 전송 큐 소비자 설정. */
	private WorkerConfig notification = new WorkerConfig(400);

	/** 교환 사이클 탐지 워커 설정. */
	private WorkerConfig cycleDetection = new WorkerConfig(1);

	@Getter
	@Setter
	public static class WorkerConfig {

		/** PGMQ visibility timeout (초). 메시지가 재노출되기까지의 잠금 시간. */
		private int visibilityTimeout = 30;

		/** 1회 폴링 시 읽어오는 최대 메시지 수. */
		private int batchSize;

		/** 읽기 횟수(read_ct)가 이 값을 초과하면 아카이브 처리한다. */
		private int maxRetryCount = 5;

		public WorkerConfig() {
		}

		public WorkerConfig(int batchSize) {
			this.batchSize = batchSize;
		}
	}
}
