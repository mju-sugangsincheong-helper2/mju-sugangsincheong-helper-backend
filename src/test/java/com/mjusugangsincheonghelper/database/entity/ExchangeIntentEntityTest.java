package com.mjusugangsincheonghelper.database.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExchangeIntentEntity 단위 테스트")
class ExchangeIntentEntityTest {

	@Nested
	@DisplayName("markDeleted 메서드는")
	class Describe_markDeleted {

		@Test
		@DisplayName("isDeleted를 true로 설정한다")
		void it_sets_isDeleted_to_true() {
			// Given
			ExchangeIntentEntity entity = ExchangeIntentEntity.builder()
					.term("202510")
					.memberId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			assertThat(entity.isDeleted()).isFalse();

			// When
			entity.markDeleted();

			// Then
			assertThat(entity.isDeleted()).isTrue();
			assertThat(entity.getDeletedAt()).isNotNull();
		}
	}

	@Nested
	@DisplayName("생성자는")
	class Describe_constructor {

		@Test
		@DisplayName("기본값으로 isDeleted가 false이다")
		void it_sets_isDeleted_to_false_by_default() {
			// Given & When
			ExchangeIntentEntity entity = ExchangeIntentEntity.builder()
					.term("202510")
					.memberId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			// Then
			assertThat(entity.isDeleted()).isFalse();
			assertThat(entity.getDeletedAt()).isNull();
		}
	}
}
