package com.mjusugangsincheonghelper.global.config;

import com.mjusugangsincheonghelper.global.api.docs.ErrorResponsesOperationCustomizer;
import com.mjusugangsincheonghelper.global.api.envelope.ErrorResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.exception.ErrorDetail;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
