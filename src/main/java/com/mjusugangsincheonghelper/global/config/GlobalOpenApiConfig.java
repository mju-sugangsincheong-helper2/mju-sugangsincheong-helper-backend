package com.mjusugangsincheonghelper.global.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.global.api.docs.ErrorResponsesOperationCustomizer;
import com.mjusugangsincheonghelper.global.api.envelope.ErrorResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.exception.ErrorDetail;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class GlobalOpenApiConfig {

	/**
	 * 스웨거 스키마를 런타임 Jackson과 동일한 field 기준으로 만든다.
	 *
	 * <p>swagger-core 기본 resolver는 getter 기준으로 이름을 뽑아서
	 * {@code getTClickCourse} → {@code tclickCourse} 처럼 틀어진다
	 * (앞 두 글자가 대문자인 게터의 mangling 규칙 때문).
	 * 런타임은 {@code spring.jackson.visibility}로 field 기준이라
	 * 문서 예시를 복사하면 400이 나는 엇박이 생긴다.
	 * {@link ModelConverter} 빈은 springdoc의 ModelConverterRegistrar가
	 * {@link ModelConverters} 최우선 순위로 등록하므로 별도 설정 없이 적용된다.
	 */
	@Bean
	public ModelResolver fieldBasedModelResolver() {
		ObjectMapper mapper = Json.mapper().copy()
				.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
				.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
				.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
		return new ModelResolver(mapper);
	}

	@Bean
	public OperationCustomizer errorResponsesOperationCustomizer() {
		return new ErrorResponsesOperationCustomizer();
	}

	@SuppressWarnings({"rawtypes"})
	@Bean
	public OpenApiCustomizer errorEnvelopeSchemaCustomizer() {
		return openApi -> {
			Components components = openApi.getComponents();
			if (components == null) {
				components = new Components();
				openApi.setComponents(components);
			}

			Map<String, Schema> errorEnvelopeSchemas = ModelConverters.getInstance().read(ErrorResponseEnvelope.class);
			errorEnvelopeSchemas.forEach(components::addSchemas);

			Map<String, Schema> errorDetailSchemas = ModelConverters.getInstance().read(ErrorDetail.class);
			errorDetailSchemas.forEach(components::addSchemas);

			Map<String, Schema> fieldViolationSchemas = ModelConverters.getInstance().read(ErrorDetail.FieldViolation.class);
			fieldViolationSchemas.forEach(components::addSchemas);
		};
	}

	@Bean
	@Profile("prod")
	public OpenAPI prodOpenAPI() {
		return new OpenAPI()
				.components(new Components()
						.addSecuritySchemes("cookieAuth", new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.COOKIE)
								.name("access_token"))
				)
				.addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
				.info(new io.swagger.v3.oas.models.info.Info()
						.title("MJU Sugangsincheong Helper API")
						.version("1.0")
						.description("명지대 수강신청 도우미 백엔드 API")
				);
	}

	@Bean
	@Profile("dev")
	public OpenAPI devOpenAPI() {
		return new OpenAPI()
				.components(new Components()
						.addSecuritySchemes("bearerAuth", new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT"))
				)
				.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
				.info(new io.swagger.v3.oas.models.info.Info()
						.title("MJU Sugangsincheong Helper API")
						.version("1.0")
						.description("인증 방법\n\n" +
								"1. `GET /api/v1/auth/config/google` → Client ID 확인\n" +
								"2. `POST /api/v1/auth/oauth/start` → Google 인증 URL 획득\n" +
								"3. Google 로그인 후 code 획득\n" +
								"4. `POST /api/v1/auth/token` {code, state} → JWT 발급\n" +
								"5. 발급받은 accessToken을 Bearer Auth에 입력\n\n" +
								"또는 `/api/v1/auth/guest` 응답의 accessToken을 Bearer Auth에 수동 입력")
				);
	}
}
