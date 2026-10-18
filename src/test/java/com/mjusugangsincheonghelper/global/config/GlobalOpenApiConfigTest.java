package com.mjusugangsincheonghelper.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 스웨거 스키마 이름 = 런타임 Jackson 키 강제 테스트.
 *
 * <p>swagger-core 기본 resolver는 getter 기준이라 getTClickCourse → tclickCourse 처럼
 * 앞 두 글자가 대문자인 게터 이름이 틀어진다. 런타임은 field 기준이므로 문서 예시를
 * 복사하면 400이 난다. fieldBasedModelResolver 빈이 이 엇박을 막는지 검증한다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GlobalOpenApiConfig.class)
class GlobalOpenApiConfigTest {

	@Autowired
	private ModelResolver fieldBasedModelResolver;

	private static Map<String, Schema> readWith(ModelResolver resolver, Class<?> type) {
		ModelConverters converters = new ModelConverters();
		converters.addConverter(resolver);
		return converters.read(type);
	}

	@Test
	@DisplayName("스웨거 스키마는 tEnterMain/tClick*/tTotal을 필드명 그대로 노출한다")
	void schemaUsesFieldNames() {
		Map<String, Schema> save = readWith(fieldBasedModelResolver, SingleGameSaveRequest.class);
		assertThat(save.get("SingleGameSaveRequest").getProperties())
				.containsKeys("totalCourses", "completed", "tEnterMain", "details")
				.doesNotContainKeys("tenterMain");

		Map<String, Schema> detail = readWith(fieldBasedModelResolver, SingleGameDetailRequest.class);
		assertThat(detail.get("SingleGameDetailRequest").getProperties())
				.containsKeys("sequence", "tClickCourse", "tClickYes", "tClickOk")
				.doesNotContainKeys("tclickCourse", "tclickYes", "tclickOk");

		Map<String, Schema> record = readWith(fieldBasedModelResolver, MyRecordResponse.class);
		assertThat(record.get("MyRecordResponse").getProperties())
				.containsKeys("gameId", "totalCourses", "completed", "tTotal", "createdAt")
				.doesNotContainKeys("ttotal");
	}
}
