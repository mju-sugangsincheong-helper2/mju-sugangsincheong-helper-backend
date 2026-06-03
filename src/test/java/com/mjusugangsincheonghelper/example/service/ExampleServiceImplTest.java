package com.mjusugangsincheonghelper.example.service;

import com.mjusugangsincheonghelper.database.entity.ExampleEntity;
import com.mjusugangsincheonghelper.database.repository.ExampleRepository;
import com.mjusugangsincheonghelper.example.dto.ExampleCreateRequest;
import com.mjusugangsincheonghelper.example.dto.ExampleDetailResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleEchoRequest;
import com.mjusugangsincheonghelper.example.dto.ExamplePageItem;
import com.mjusugangsincheonghelper.example.dto.ExampleResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleUpdateRequest;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExampleServiceImpl 단위 테스트")
class ExampleServiceImplTest {

	@Mock
	private ExampleRepository exampleRepository;

	@InjectMocks
	private ExampleServiceImpl exampleService;

	@Nested
	@DisplayName("hello 메서드는")
	class Describe_hello {

		@Test
		@DisplayName("이름을 받아 ExampleResponse를 반환한다")
		void it_returns_example_response_with_name() {
			ExampleResponse response = exampleService.hello("world");

			assertThat(response.getMessage()).isEqualTo("hello world");
			assertThat(response.getTimestamp()).isNotNull();
		}
	}

	@Nested
	@DisplayName("echo 메서드는")
	class Describe_echo {

		@Test
		@DisplayName("요청 메시지를 그대로 반환한다")
		void it_echoes_request_message() {
			ExampleEchoRequest request = ExampleEchoRequest.builder()
					.message("test message")
					.build();

			ExampleResponse response = exampleService.echo(request);

			assertThat(response.getMessage()).isEqualTo("test message");
		}
	}

	@Nested
	@DisplayName("create 메서드는")
	class Describe_create {

		@Test
		@DisplayName("요청을 받아 엔티티를 저장하고 응답을 반환한다")
		void it_saves_entity_and_returns_response() {
			ExampleCreateRequest request = ExampleCreateRequest.builder()
					.title("Test Title")
					.content("Test Content")
					.build();

			ExampleEntity savedEntity = ExampleEntity.builder()
					.title("Test Title")
					.content("Test Content")
					.build();

			given(exampleRepository.save(any(ExampleEntity.class))).willReturn(savedEntity);

			ExampleDetailResponse response = exampleService.create(request);

			assertThat(response.getTitle()).isEqualTo("Test Title");
			assertThat(response.getContent()).isEqualTo("Test Content");
			assertThat(response.isActive()).isTrue();
			verify(exampleRepository).save(any(ExampleEntity.class));
		}
	}

	@Nested
	@DisplayName("findById 메서드는")
	class Describe_findById {

		@Test
		@DisplayName("존재하는 ID면 엔티티를 반환한다")
		void it_returns_entity_when_exists() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Test")
					.content("Content")
					.build();

			given(exampleRepository.findById(1L)).willReturn(Optional.of(entity));

			ExampleDetailResponse response = exampleService.findById(1L);

			assertThat(response.getTitle()).isEqualTo("Test");
		}

		@Test
		@DisplayName("존재하지 않는 ID면 GLOBAL_NOT_FOUND 예외를 발생시킨다")
		void it_throws_exception_when_not_found() {
			given(exampleRepository.findById(999L)).willReturn(Optional.empty());

			assertThatThrownBy(() -> exampleService.findById(999L))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.GLOBAL_NOT_FOUND);
					});
		}
	}

	@Nested
	@DisplayName("list 메서드는")
	class Describe_list {

		@Test
		@DisplayName("활성화된 엔티티만 페이징하여 반환한다")
		void it_returns_paged_active_entities() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Test")
					.content("Content")
					.build();

			PageRequest pageRequest = PageRequest.of(0, 10);
			Page<ExampleEntity> entityPage = new PageImpl<>(List.of(entity), pageRequest, 1);

			given(exampleRepository.findByActiveTrue(any(PageRequest.class))).willReturn(entityPage);

			Page<ExamplePageItem> result = exampleService.list(0, 10);

			assertThat(result.getContent()).hasSize(1);
			assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test");
		}
	}

	@Nested
	@DisplayName("update 메서드는")
	class Describe_update {

		@Test
		@DisplayName("존재하는 엔티티를 수정한다")
		void it_updates_existing_entity() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Old Title")
					.content("Old Content")
					.build();

			ExampleUpdateRequest request = ExampleUpdateRequest.builder()
					.title("New Title")
					.content("New Content")
					.build();

			given(exampleRepository.findById(1L)).willReturn(Optional.of(entity));

			ExampleDetailResponse response = exampleService.update(1L, request);

			assertThat(response.getTitle()).isEqualTo("New Title");
			assertThat(response.getContent()).isEqualTo("New Content");
		}

		@Test
		@DisplayName("존재하지 않는 ID면 GLOBAL_NOT_FOUND 예외를 발생시킨다")
		void it_throws_exception_when_not_found() {
			ExampleUpdateRequest request = ExampleUpdateRequest.builder()
					.title("Title")
					.content("Content")
					.build();

			given(exampleRepository.findById(999L)).willReturn(Optional.empty());

			assertThatThrownBy(() -> exampleService.update(999L, request))
					.isInstanceOf(BaseException.class);
		}
	}

	@Nested
	@DisplayName("delete 메서드는")
	class Describe_delete {

		@Test
		@DisplayName("엔티티를 비활성화한다")
		void it_deactivates_entity() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Test")
					.content("Content")
					.build();

			given(exampleRepository.findById(1L)).willReturn(Optional.of(entity));

			exampleService.delete(1L);

			assertThat(entity.isActive()).isFalse();
		}

		@Test
		@DisplayName("존재하지 않는 ID면 GLOBAL_NOT_FOUND 예외를 발생시킨다")
		void it_throws_exception_when_not_found() {
			given(exampleRepository.findById(999L)).willReturn(Optional.empty());

			assertThatThrownBy(() -> exampleService.delete(999L))
					.isInstanceOf(BaseException.class);
		}
	}

	@Nested
	@DisplayName("throwNotFound 메서드는")
	class Describe_throwNotFound {

		@Test
		@DisplayName("GLOBAL_NOT_FOUND 예외를 발생시킨다")
		void it_throws_not_found_exception() {
			assertThatThrownBy(() -> exampleService.throwNotFound())
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> {
						BaseException baseException = (BaseException) ex;
						assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.GLOBAL_NOT_FOUND);
					});
		}
	}
}
