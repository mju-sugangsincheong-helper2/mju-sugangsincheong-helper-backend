package com.mjusugangsincheonghelper.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄링 태스크의 유일한 실행 모델.
 *
 * <p>이 프로젝트에는 별도의 {@code @Async} 풀이 없다. 모든 비동기/주기적 작업은
 * {@code @Scheduled}로 선언하고, 성격이 다른 작업군이 서로의 스레드를 굶주리게 하지
 * 않도록 용도별 {@link TaskScheduler} 풀로 격리한다:
 *
 * <ul>
 *   <li>{@code taskScheduler} (primary): 짧은 일회성 지연 작업(기타 단발성 스케줄)</li>
 *   <li>{@code pgmqScheduler}: PGMQ 폴링 워커(notification/cycle-detection)와 아카이브 정리 크론</li>
 *   <li>{@code multigameScheduler}: 게임 라이프사이클 크론 — start는 공급 램프업 동안
 *       스레드를 점유하므로(약 30초) 별도 풀로 격리</li>
 * </ul>
 *
 * <p>새 주기 작업을 추가할 때는 새 풀을 만들기보다 위 세 풀 중 용도에 맞는 것을
 * {@code @Scheduled(scheduler = "...")}로 선택해 재사용한다.
 */
@EnableScheduling
@Configuration
@RequiredArgsConstructor
public class GlobalSchedulingConfig {

	private final SchedulingProperties properties;

	@Bean
	@Primary
	public TaskScheduler taskScheduler() {
		return scheduler(properties.getTask());
	}

	@Bean("pgmqScheduler")
	public TaskScheduler pgmqScheduler() {
		return scheduler(properties.getPgmq());
	}

	@Bean("multigameScheduler")
	public TaskScheduler multigameScheduler() {
		return scheduler(properties.getMultigame());
	}

	private ThreadPoolTaskScheduler scheduler(SchedulingProperties.Pool pool) {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(pool.getPoolSize());
		scheduler.setThreadNamePrefix(pool.getThreadNamePrefix());
		return scheduler;
	}
}