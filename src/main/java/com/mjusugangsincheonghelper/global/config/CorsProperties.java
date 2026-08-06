package com.mjusugangsincheonghelper.global.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

	/**
	 * 허용할 Origin 목록 (app.cors.allowed-origins).
	 *
	 * <p>credential 요청(allowCredentials=true)을 지원하므로 "*"는 사용할 수 없다.
	 * (Spring이 "When allowCredentials is true, allowedOrigins cannot contain
	 * the special value '*'." 검증 예외를 던진다.) 특정 Origin을 명시하거나
	 * allowedOriginPatterns를 사용해야 한다.
	 *
	 * <p>미설정 시 빈 목록 = 모든 교차 출처 요청 차단(fail-closed)이 기본 동작이다.
	 * dev/prod 모두 설정값을 명시하므로 실제로는 빈 목록이 거의 쓰이지 않는다.
	 */
	private List<String> allowedOrigins = List.of();
}
