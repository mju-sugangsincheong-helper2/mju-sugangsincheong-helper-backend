package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExampleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ExampleRepository 테스트")
class ExampleRepositoryTest {

	@Autowired
	private ExampleRepository exampleRepository;

	@BeforeEach
	void setUp() {
		exampleRepository.deleteAll();
	}

	@Nested
	@DisplayName("save 메서드는")
	class Describe_save {

		@Test
		@DisplayName("엔티티를 저장하고 ID를 부여한다")
		void it_saves_entity_and_assigns_id() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Test Title")
					.content("Test Content")
					.build();

			ExampleEntity saved = exampleRepository.save(entity);

			assertThat(saved.getId()).isNotNull();
			assertThat(saved.getTitle()).isEqualTo("Test Title");
		}
	}

	@Nested
	@DisplayName("findById 메서드는")
	class Describe_findById {

		@Test
		@DisplayName("저장된 엔티티를 ID로 조회한다")
		void it_finds_entity_by_id() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Test")
					.content("Content")
					.build();
			ExampleEntity saved = exampleRepository.save(entity);

			ExampleEntity found = exampleRepository.findById(saved.getId()).orElse(null);

			assertThat(found).isNotNull();
			assertThat(found.getTitle()).isEqualTo("Test");
		}

		@Test
		@DisplayName("존재하지 않는 ID면 empty를 반환한다")
		void it_returns_empty_when_not_found() {
			assertThat(exampleRepository.findById(999L)).isEmpty();
		}
	}

	@Nested
	@DisplayName("findByActiveTrue 메서드는")
	class Describe_findByActiveTrue {

		@Test
		@DisplayName("활성화된 엔티티만 반환한다")
		void it_returns_only_active_entities() {
			ExampleEntity active1 = ExampleEntity.builder()
					.title("Active 1")
					.content("Content")
					.build();
			ExampleEntity active2 = ExampleEntity.builder()
					.title("Active 2")
					.content("Content")
					.build();
			ExampleEntity inactive = ExampleEntity.builder()
					.title("Inactive")
					.content("Content")
					.build();
			inactive.deactivate();

			exampleRepository.save(active1);
			exampleRepository.save(active2);
			exampleRepository.save(inactive);

			Page<ExampleEntity> result = exampleRepository.findByActiveTrue(PageRequest.of(0, 10));

			assertThat(result.getContent()).hasSize(2);
			assertThat(result.getContent())
					.extracting(ExampleEntity::getTitle)
					.containsExactlyInAnyOrder("Active 1", "Active 2");
		}

		@Test
		@DisplayName("페이징을 지원한다")
		void it_supports_paging() {
			for (int i = 1; i <= 15; i++) {
				exampleRepository.save(ExampleEntity.builder()
						.title("Title " + i)
						.content("Content")
						.build());
			}

			Page<ExampleEntity> page1 = exampleRepository.findByActiveTrue(PageRequest.of(0, 10));
			Page<ExampleEntity> page2 = exampleRepository.findByActiveTrue(PageRequest.of(1, 10));

			assertThat(page1.getContent()).hasSize(10);
			assertThat(page2.getContent()).hasSize(5);
			assertThat(page1.getTotalElements()).isEqualTo(15);
			assertThat(page1.getTotalPages()).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("delete 메서드는")
	class Describe_delete {

		@Test
		@DisplayName("엔티티를 삭제한다")
		void it_deletes_entity() {
			ExampleEntity entity = ExampleEntity.builder()
					.title("Test")
					.content("Content")
					.build();
			ExampleEntity saved = exampleRepository.save(entity);

			exampleRepository.delete(saved);

			assertThat(exampleRepository.findById(saved.getId())).isEmpty();
		}
	}
}
