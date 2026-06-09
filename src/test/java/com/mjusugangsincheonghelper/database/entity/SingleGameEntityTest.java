package com.mjusugangsincheonghelper.database.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SingleGameEntity 단위 테스트")
class SingleGameEntityTest {

	@Nested
	@DisplayName("생성자는")
	class Describe_constructor {

		@Test
		@DisplayName("모든 필드로 엔티티를 생성한다")
		void it_creates_entity_with_all_fields() {
			SingleGameEntity entity = SingleGameEntity.builder()
					.memberId(1L)
					.tTotal(5000)
					.tEnterMain(200)
					.isCompleted(true)
					.totalCourses(6)
					.build();

			assertThat(entity.getMemberId()).isEqualTo(1L);
			assertThat(entity.getTTotal()).isEqualTo(5000);
			assertThat(entity.getTEnterMain()).isEqualTo(200);
			assertThat(entity.isCompleted()).isTrue();
			assertThat(entity.getTotalCourses()).isEqualTo(6);
		}

		@Test
		@DisplayName("타임아웃 시 isCompleted는 false이다")
		void it_creates_entity_with_timeout() {
			SingleGameEntity entity = SingleGameEntity.builder()
					.memberId(1L)
					.tTotal(60000)
					.tEnterMain(500)
					.isCompleted(false)
					.totalCourses(8)
					.build();

			assertThat(entity.isCompleted()).isFalse();
		}
	}
}
