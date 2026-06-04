package com.mjusugangsincheonghelper.global.config;

import com.mjusugangsincheonghelper.global.api.docs.ErrorResponsesOperationCustomizer;
import com.mjusugangsincheonghelper.global.api.envelope.ErrorResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.exception.ErrorDetail;
import io.swagger.v3.core.converter.ModelConverters;
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
						.description("### 🔐 인증 방법\n\n" +
								"1. `/api/v1/auth/guest` API를 호출하여 토큰을 발급받으세요.\n" +
								"2. 응답의 `Set-Cookie` 헤더에서 `access_token` 값을 확인하세요.\n" +
								"3. 상단 [Authorize] 버튼 클릭 후 `Bearer {token}` 형식으로 입력하세요.")
				);
	}
}
