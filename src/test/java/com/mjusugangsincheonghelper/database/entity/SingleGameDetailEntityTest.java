package com.mjusugangsincheonghelper.database.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SingleGameDetailEntity 단위 테스트")
class SingleGameDetailEntityTest {

	@Nested
	@DisplayName("생성자는")
	class Describe_constructor {

		@Test
		@DisplayName("모든 필드로 엔티티를 생성한다")
		void it_creates_entity_with_all_fields() {
			SingleGameDetailEntity entity = SingleGameDetailEntity.builder()
					.gameId(1L)
					.sequence(1)
					.tClickCourse(450)
					.tClickYes(180)
					.tClickOk(200)
					.build();

			assertThat(entity.getGameId()).isEqualTo(1L);
			assertThat(entity.getSequence()).isEqualTo(1);
			assertThat(entity.getTClickCourse()).isEqualTo(450);
			assertThat(entity.getTClickYes()).isEqualTo(180);
			assertThat(entity.getTClickOk()).isEqualTo(200);
		}

		@Test
		@DisplayName("복합키(gameId, sequence)를 가진다")
		void it_has_composite_key() {
			SingleGameDetailId id = new SingleGameDetailId(1L, 2);

			assertThat(id.getGameId()).isEqualTo(1L);
			assertThat(id.getSequence()).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("SingleGameDetailId는")
	class Describe_compositeKeyId {

		@Test
		@DisplayName("동일한 gameId와 sequence면 동등하다")
		void it_equals_when_same_fields() {
			SingleGameDetailId id1 = new SingleGameDetailId(1L, 1);
			SingleGameDetailId id2 = new SingleGameDetailId(1L, 1);

			assertThat(id1).isEqualTo(id2);
			assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
		}

		@Test
		@DisplayName("다르면 동등하지 않다")
		void it_not_equals_when_different() {
			SingleGameDetailId id1 = new SingleGameDetailId(1L, 1);
			SingleGameDetailId id2 = new SingleGameDetailId(1L, 2);

			assertThat(id1).isNotEqualTo(id2);
		}
	}
}
