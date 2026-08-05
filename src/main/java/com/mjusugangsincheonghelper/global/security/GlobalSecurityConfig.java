package com.mjusugangsincheonghelper.global.security;

import com.mjusugangsincheonghelper.global.security.filter.ConsentCheckFilter;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class GlobalSecurityConfig {

	public static final String[] PUBLIC_URLS = {
			"/api/*/auth/guest",
			"/api/*/auth/refresh",
			"/api/*/auth/login/google/merge",
			"/api/*/auth/oauth/start",
			"/api/*/auth/token",
			"/api/*/auth/config/google",
			"/api/*/auth/test-**",
			"/api/*/example/**",
			"/swagger-ui/**",
			"/v3/api-docs/**",
			"/*.html",
			"/*.js"
	};

	/**
	 * HTTP 메서드별 공개 API 목록.
	 * 같은 경로의 다른 HTTP 메서드는 보안 체인을 그대로 타도록 메서드별로 분리하여 정의한다.
	 */
	public static final String[] PUBLIC_GET_URLS = {
			"/api/*/course/sections",
			"/api/*/course/department",
			"/api/*/exchange/intents/recent",
			"/api/*/notices"
	};

	public static final String[] PUBLIC_POST_URLS = {};

	public static final String[] PUBLIC_PUT_URLS = {};

	public static final String[] PUBLIC_PATCH_URLS = {};

	public static final String[] PUBLIC_DELETE_URLS = {};

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ConsentCheckFilter consentCheckFilter;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	@Order(1)
	public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatchers(matchers -> {
					matchers.requestMatchers(PUBLIC_URLS);
					if (PUBLIC_GET_URLS.length > 0) {
						matchers.requestMatchers(HttpMethod.GET, PUBLIC_GET_URLS);
					}
					if (PUBLIC_POST_URLS.length > 0) {
						matchers.requestMatchers(HttpMethod.POST, PUBLIC_POST_URLS);
					}
					if (PUBLIC_PUT_URLS.length > 0) {
						matchers.requestMatchers(HttpMethod.PUT, PUBLIC_PUT_URLS);
					}
					if (PUBLIC_PATCH_URLS.length > 0) {
						matchers.requestMatchers(HttpMethod.PATCH, PUBLIC_PATCH_URLS);
					}
					if (PUBLIC_DELETE_URLS.length > 0) {
						matchers.requestMatchers(HttpMethod.DELETE, PUBLIC_DELETE_URLS);
					}
				})
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain securedSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatchers(matchers -> {
					matchers.requestMatchers("/api/**");
					matchers.requestMatchers("/actuator/**");
				})
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
							// Actuator는 관리자 전용 (PUBLIC_URLS에서 제거됨)
							.requestMatchers("/actuator/**").hasRole("ADMIN")
							.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(consentCheckFilter, JwtAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST");
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173"));
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
		configuration.setAllowedHeaders(Arrays.asList("*"));
		configuration.setExposedHeaders(Arrays.asList(
				"Authorization",
				"X-Access-Token",
				"X-Refresh-Token",
				"X-Request-Id",
				"X-Api-Version",
				"Set-Cookie"
		));
		configuration.setMaxAge(3600L);
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
