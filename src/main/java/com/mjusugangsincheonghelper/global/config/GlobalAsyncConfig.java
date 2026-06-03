package com.mjusugangsincheonghelper.global.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
public class GlobalAsyncConfig implements AsyncConfigurer {

	@Bean(name = "globalTaskExecutor")
	public Executor globalTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(1000);
		executor.setThreadNamePrefix("global-async-");
		executor.initialize();
		return executor;
	}

	@Override
	public Executor getAsyncExecutor() {
		return globalTaskExecutor();
	}
}
