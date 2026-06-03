package com.mjusugangsincheonghelper.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GlobalWebMvcConfig implements WebMvcConfigurer {

	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		configurer.usePathSegment(1);
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
	}
}
