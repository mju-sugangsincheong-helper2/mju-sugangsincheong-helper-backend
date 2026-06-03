package com.mjusugangsincheonghelper.global.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class HibernateStatisticsConfig {

	private final EntityManagerFactory entityManagerFactory;

	@PostConstruct
	public void enableStatistics() {
		entityManagerFactory.unwrap(SessionFactory.class)
				.getStatistics()
				.setStatisticsEnabled(true);
		log.info("Hibernate statistics enabled");
	}
}
