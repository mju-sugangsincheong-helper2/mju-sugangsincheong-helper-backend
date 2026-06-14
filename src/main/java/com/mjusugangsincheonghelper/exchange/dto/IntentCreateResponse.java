package com.mjusugangsincheonghelper.exchange.dto;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class IntentCreateResponse {

	private Long intentId;
	private Long memberId;
	private String giveCourseNo;
	private String wantCourseNo;
	private boolean isDeleted;
	private Instant createdAt;

	public static IntentCreateResponse from(ExchangeIntentEntity entity) {
		return IntentCreateResponse.builder()
				.intentId(entity.getId())
				.memberId(entity.getMemberId())
				.giveCourseNo(entity.getGiveCourseNo())
				.wantCourseNo(entity.getWantCourseNo())
				.isDeleted(entity.isDeleted())
				.createdAt(entity.getCreatedAt())
				.build();
	}
}
