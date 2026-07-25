package com.mjusugangsincheonghelper.multigame.database.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MultigameReservationEntity 테스트")
class MultigameReservationEntityTest {

	@Test
	@DisplayName("Builder로 엔티티를 생성할 수 있다")
	void it_creates_entity_with_builder() {
		Long memberId = 1L;
		String startTime = "20260630120000";

		MultigameReservationEntity entity = MultigameReservationEntity.builder()
				.memberId(memberId)
				.startTime(startTime)
				.build();

		assertThat(entity.getMemberId()).isEqualTo(memberId);
		assertThat(entity.getStartTime()).isEqualTo(startTime);
	}

	@Test
	@DisplayName("createdAt은 null일 수 있다 (JPA Auditing이 설정하지 않은 경우)")
	void it_has_nullable_createdAt_before_persistence() {
		MultigameReservationEntity entity = MultigameReservationEntity.builder()
				.memberId(1L)
				.startTime("20260630120000")
				.build();

		assertThat(entity.getCreatedAt()).isNull();
	}
}
