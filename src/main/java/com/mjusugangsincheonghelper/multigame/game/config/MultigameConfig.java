package com.mjusugangsincheonghelper.multigame.game.config;

import com.mjusugangsincheonghelper.multigame.game.domain.GameStatusResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultigameConfig {

	@Bean
	public GameStatusResolver gameStatusResolver(MultigameProperties properties) {
		return new GameStatusResolver(properties.getStartClose(), properties.getEndClose());
	}
}
