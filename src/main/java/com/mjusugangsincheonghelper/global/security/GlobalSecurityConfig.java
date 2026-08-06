package com.mjusugangsincheonghelper.global.security;

import com.mjusugangsincheonghelper.global.config.CorsProperties;
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

	/**
	 * 공개 URL 규칙은 yml(app.security.*)에서 관리한다.
	 * 엔드포인트를 공개/비공개로 바꾸려면 Java 수정 없이 yml만 수정하면 된다.
	 */
	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ConsentCheckFilter consentCheckFilter;
	private final CorsProperties corsProperties;
	private final AppSecurityProperties securityProperties;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	@Order(1)
	public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatchers(matchers -> {
					if (!securityProperties.getPublicUrls().isEmpty()) {
						matchers.requestMatchers(securityProperties.getPublicUrls().toArray(String[]::new));
					}
					if (!securityProperties.getPublicGetUrls().isEmpty()) {
						matchers.requestMatchers(HttpMethod.GET, securityProperties.getPublicGetUrls().toArray(String[]::new));
					}
					if (!securityProperties.getPublicPostUrls().isEmpty()) {
						matchers.requestMatchers(HttpMethod.POST, securityProperties.getPublicPostUrls().toArray(String[]::new));
					}
					if (!securityProperties.getPublicPutUrls().isEmpty()) {
						matchers.requestMatchers(HttpMethod.PUT, securityProperties.getPublicPutUrls().toArray(String[]::new));
					}
					if (!securityProperties.getPublicPatchUrls().isEmpty()) {
						matchers.requestMatchers(HttpMethod.PATCH, securityProperties.getPublicPatchUrls().toArray(String[]::new));
					}
					if (!securityProperties.getPublicDeleteUrls().isEmpty()) {
						matchers.requestMatchers(HttpMethod.DELETE, securityProperties.getPublicDeleteUrls().toArray(String[]::new));
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
				})
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
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
		configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
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
