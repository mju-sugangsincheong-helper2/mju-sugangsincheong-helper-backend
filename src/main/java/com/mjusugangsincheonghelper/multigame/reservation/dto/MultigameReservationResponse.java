package com.mjusugangsincheonghelper.multigame.reservation.dto;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultigameReservationResponse {

	private Long id;
	private Long memberId;
	private String multigameId;
	private Instant createdAt;

	public static MultigameReservationResponse from(MultigameReservationEntity entity) {
		return MultigameReservationResponse.builder()
				.id(entity.getId())
				.memberId(entity.getMemberId())
				.multigameId(entity.getStartTime())
				.createdAt(entity.getCreatedAt())
				.build();
	}
}
