package com.mjusugangsincheonghelper.global.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * Jackson 전역 설정 단일화 강제 테스트.
 *
 * wire DTO/envelope의 JSON 키는 전역 visibility(field:any/getter:none/is-getter:none)로만
 * 결정한다. per-field @JsonProperty는 금지한다. 이유:
 * - 기본 매퍼에서 boolean 게터가 deleted 같은 중복 키를 추가로 만들어
 *   per-field만으로는 매퍼 독립성이 성립하지 않음이 실측됨 (T4, 전역 제거 실험)
 * - 예외가 필요하면(키 리네임 등) 이 테스트에 예외 사유를 남기고 허용할 것
 *
 * @JsonInclude(NON_NULL) 같은 동작성 어노테이션은 허용한다.
 */
class JacksonContractTest {

	private static final List<String> BASE_PACKAGES = List.of(
			"com.mjusugangsincheonghelper.account.dto",
			"com.mjusugangsincheonghelper.auth.common.dto",
			"com.mjusugangsincheonghelper.auth.guest.dto",
			"com.mjusugangsincheonghelper.auth.merge.dto",
			"com.mjusugangsincheonghelper.auth.oauth.dto",
			"com.mjusugangsincheonghelper.auth.session.dto",
			"com.mjusugangsincheonghelper.course.dto",
			"com.mjusugangsincheonghelper.diagnostics.dto",
			"com.mjusugangsincheonghelper.example.dto",
			"com.mjusugangsincheonghelper.exchange.dto",
			"com.mjusugangsincheonghelper.global.api.envelope",
			"com.mjusugangsincheonghelper.global.api.meta",
			"com.mjusugangsincheonghelper.latency.dto",
			"com.mjusugangsincheonghelper.multigame.game.dto",
			"com.mjusugangsincheonghelper.multigame.result.dto",
			"com.mjusugangsincheonghelper.notice.dto",
			"com.mjusugangsincheonghelper.notification.consumer.dto",
			"com.mjusugangsincheonghelper.notification.dto",
			"com.mjusugangsincheonghelper.singlegame.dto",
			"com.mjusugangsincheonghelper.system.dto"
	);

	// dto 패키지 밖에 있는 wire 클래스 (개별 등록)
	private static final List<Class<?>> EXTRA_CLASSES = List.of(
			com.mjusugangsincheonghelper.auth.test.TestLoginResponse.class
	);

	@Test
	@DisplayName("wire DTO에 @JsonProperty가 있으면 실패한다 (전역 설정 단일화)")
	void noJsonPropertyOnWireDto() {
		List<String> violations = new ArrayList<>();
		for (String basePackage : BASE_PACKAGES) {
			for (Class<?> clazz : scanClasses(basePackage)) {
				checkClass(clazz, violations);
			}
		}
		for (Class<?> clazz : EXTRA_CLASSES) {
			checkClass(clazz, violations);
		}
		assertThat(violations).as("@JsonProperty 사용 금지 (클래스#필드)").isEmpty();
	}

	private List<Class<?>> scanClasses(String basePackage) {
		ClassPathScanningCandidateComponentProvider scanner =
				new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
		List<Class<?>> classes = new ArrayList<>();
		for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
			try {
				classes.add(Class.forName(bd.getBeanClassName()));
			} catch (ClassNotFoundException | NoClassDefFoundError e) {
				// 테스트 클래스패스에 없는 의존성은 스킵
			}
		}
		return classes;
	}

	private void checkClass(Class<?> clazz, List<String> violations) {
		if (clazz.isEnum() || clazz.isInterface() || clazz.isAnnotation()) {
			return;
		}
		if (clazz.getSimpleName().endsWith("Builder")) {
			return;  // Lombok @Builder 생성 클래스 (직렬화 대상 아님)
		}
		if (clazz.isRecord()) {
			checkRecord(clazz, violations);
		}
		for (Field field : clazz.getDeclaredFields()) {
			int mod = field.getModifiers();
			if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || field.isSynthetic()) {
				continue;
			}
			if (field.getAnnotation(JsonProperty.class) != null) {
				violations.add(clazz.getName() + "#" + field.getName());
			}
		}
		for (Class<?> nested : clazz.getDeclaredClasses()) {
			checkClass(nested, violations);
		}
	}

	private void checkRecord(Class<?> clazz, List<String> violations) {
		for (var component : clazz.getRecordComponents()) {
			if (hasRecordComponentAnnotation(clazz, component.getName(), JsonProperty.class)) {
				violations.add(clazz.getName() + "#" + component.getName());
			}
		}
		// 메서드에 직접 붙은 @JsonProperty도 금지
		for (var method : clazz.getDeclaredMethods()) {
			if (method.getAnnotation(JsonProperty.class) != null) {
				violations.add(clazz.getName() + "#" + method.getName() + "()");
			}
		}
	}

	private boolean hasRecordComponentAnnotation(Class<?> clazz, String name,
			Class<? extends java.lang.annotation.Annotation> annotation) {
		try {
			if (clazz.getDeclaredField(name).getAnnotation(annotation) != null) {
				return true;
			}
		} catch (NoSuchFieldException e) {
			// fall through
		}
		try {
			if (clazz.getMethod(name).getAnnotation(annotation) != null) {
				return true;
			}
		} catch (NoSuchMethodException e) {
			// fall through
		}
		for (var ctor : clazz.getDeclaredConstructors()) {
			for (var param : ctor.getParameters()) {
				if (param.getName().equals(name) && param.getAnnotation(annotation) != null) {
					return true;
				}
			}
		}
		return false;
	}
}
