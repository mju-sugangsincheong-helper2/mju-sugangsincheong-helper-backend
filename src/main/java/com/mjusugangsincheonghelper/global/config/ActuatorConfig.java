package com.mjusugangsincheonghelper.global.config;

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator 엔드포인트 구동에 필요한 빈.
 * httpexchanges는 HttpExchangeRepository 빈이 등록되어야 엔드포인트가 노출된다.
 */
@Configuration
public class ActuatorConfig {

	@Bean
	public HttpExchangeRepository httpExchangeRepository() {
		return new InMemoryHttpExchangeRepository();
	}
}
