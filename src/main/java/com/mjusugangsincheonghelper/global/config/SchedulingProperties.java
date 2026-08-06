package com.mjusugangsincheonghelper.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Scheduled 스케줄러별 스레드 풀 설정 (app.scheduling.*).
 *
 * <p>task: 기본(@Primary) 스케줄러, pgmq: DB 작업 큐 폴링/정리,
 * multigame: 게임 라이프사이클 배치. 운영 부하에 따라 프로필 yml에서 오버라이드한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.scheduling")
public class SchedulingProperties {

	private Pool task = pool(2, "global-scheduler-");
	private Pool pgmq = pool(2, "pgmq-worker-");
	private Pool multigame = pool(1, "multigame-scheduler-");

	private static Pool pool(int poolSize, String threadNamePrefix) {
		Pool pool = new Pool();
		pool.setPoolSize(poolSize);
		pool.setThreadNamePrefix(threadNamePrefix);
		return pool;
	}

	@Getter
	@Setter
	public static class Pool {

		private int poolSize;
		private String threadNamePrefix;
	}
}