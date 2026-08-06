package com.mjusugangsincheonghelper.global.security;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 공개 URL 설정 (app.security.*).
 *
 * <p>엔드포인트의 공개/비공개 여부를 yml에서만 관리하기 위한 단일 소스.
 * 코드 수정 없이 yml만 바꾸면 공개 규칙이 적용된다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

	/** HTTP 메서드와 관계없이 공개하는 URL 패턴 */
	private List<String> publicUrls = List.of();

	/** HTTP 메서드별로만 공개하는 URL 패턴 (그 외 메서드는 인증 필수) */
	private List<String> publicGetUrls = List.of();
	private List<String> publicPostUrls = List.of();
	private List<String> publicPutUrls = List.of();
	private List<String> publicPatchUrls = List.of();
	private List<String> publicDeleteUrls = List.of();
}
