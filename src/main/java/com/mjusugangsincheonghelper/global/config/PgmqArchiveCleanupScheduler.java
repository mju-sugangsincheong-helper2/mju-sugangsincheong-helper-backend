package com.mjusugangsincheonghelper.global.config;

import com.mjusugangsincheonghelper.exchange.service.ExchangeCycleDetector;
import com.mjusugangsincheonghelper.notification.consumer.NotificationConsumerWorker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * pgmq 아카이브 테이블(pgmq.a_*)은 소비 후에도 무한히 쌓인다.
 * 보관 기간(retention)이 지난 아카이브 행을 매일 주기적으로 삭제한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgmqArchiveCleanupScheduler {

	private static final List<String> QUEUE_NAMES = List.of(
			NotificationConsumerWorker.QUEUE_NAME,
			ExchangeCycleDetector.QUEUE_NAME
	);

	private final JdbcTemplate jdbcTemplate;

	@Value("${app.pgmq.archive-retention-days:7}")
	private long retentionDays;

	@Scheduled(cron = "${app.schedule.pgmq-cleanup.cron:0 30 3 * * *}", scheduler = "pgmqScheduler")
	public void purgeArchives() {
		for (String queueName : QUEUE_NAMES) {
			try {
				int deleted = jdbcTemplate.update(
						"DELETE FROM pgmq.a_" + queueName + " WHERE enqueued_at < now() - (? || ' days')::interval",
						retentionDays);
				log.info("PGMQ archive purged: queue={}, deleted={}, retentionDays={}", queueName, deleted, retentionDays);
			} catch (Exception e) {
				log.warn("PGMQ archive purge skipped (queue may not exist): queue={}, reason={}", queueName, e.getMessage());
			}
		}
	}
}
