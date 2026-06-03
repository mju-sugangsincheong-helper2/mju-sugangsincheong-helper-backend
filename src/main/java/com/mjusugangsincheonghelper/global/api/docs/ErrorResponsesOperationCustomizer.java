package com.mjusugangsincheonghelper.global.api.docs;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springdoc.core.customizers.OperationCustomizer;

public class ErrorResponsesOperationCustomizer implements OperationCustomizer {

	@Override
	public Operation customize(Operation operation, HandlerMethod handlerMethod) {
		OperationErrorCodes annotation = AnnotatedElementUtils.findMergedAnnotation(
				handlerMethod.getMethod(),
				OperationErrorCodes.class
		);
		if (annotation == null) {
			return operation;
		}

		ApiResponses responses = operation.getResponses();
		if (responses == null) {
			responses = new ApiResponses();
			operation.setResponses(responses);
		}

		for (ErrorCode errorCode : annotation.value()) {
			String status = String.valueOf(errorCode.getStatus().value());
			ApiResponse response = responses.get(status);
			if (response == null) {
				response = new ApiResponse();
				responses.addApiResponse(status, response);
			}
			response.description(errorCode.getMessage());
			response.content(defaultErrorContent());
		}

		return operation;
	}

	private Content defaultErrorContent() {
		Content content = new Content();
		content.addMediaType(MediaType.APPLICATION_JSON_VALUE,
				new io.swagger.v3.oas.models.media.MediaType()
						.schema(new Schema<>().$ref("#/components/schemas/ErrorResponseEnvelope")));
		return content;
	}
}
