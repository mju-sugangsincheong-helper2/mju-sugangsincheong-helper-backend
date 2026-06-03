package com.mjusugangsincheonghelper.database.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExampleEntity 단위 테스트")
class ExampleEntityTest {

	@Nested
	@DisplayName("생성자는")
	class Describe_constructor {

		@Test
		@DisplayName("title과 content로 엔티티를 생성하고 active는 true로 설정한다")
		void it_creates_entity_with_active_true() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Test Title")
					.content("Test Content")
					.build();

			assertThat(entity.getTitle()).isEqualTo("Test Title");
			assertThat(entity.getContent()).isEqualTo("Test Content");
			assertThat(entity.isActive()).isTrue();
		}
	}

	@Nested
	@DisplayName("deactivate 메서드는")
	class Describe_deactivate {

		@Test
		@DisplayName("active를 false로 변경한다")
		void it_sets_active_to_false() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Test")
					.content("Content")
					.build();

			entity.deactivate();

			assertThat(entity.isActive()).isFalse();
		}
	}

	@Nested
	@DisplayName("activate 메서드는")
	class Describe_activate {

		@Test
		@DisplayName("active를 true로 변경한다")
		void it_sets_active_to_true() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Test")
					.content("Content")
					.build();
			entity.deactivate();

			entity.activate();

			assertThat(entity.isActive()).isTrue();
		}
	}

	@Nested
	@DisplayName("update 메서드는")
	class Describe_update {

		@Test
		@DisplayName("title과 content를 수정한다")
		void it_updates_title_and_content() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Old Title")
					.content("Old Content")
					.build();

			entity.update("New Title", "New Content");

			assertThat(entity.getTitle()).isEqualTo("New Title");
			assertThat(entity.getContent()).isEqualTo("New Content");
		}
	}
}
