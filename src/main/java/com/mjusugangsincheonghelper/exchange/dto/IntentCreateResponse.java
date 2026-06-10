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

	private final String message;
	private final Instant timestamp;
	private final Long intentId;
	private final Long memberId;
	private final String giveCourseNo;
	private final String wantCourseNo;
	private final boolean isDeleted;

	public static IntentCreateResponse from(ExchangeIntentEntity entity) {
		return IntentCreateResponse.builder()
				.message("교환 의사가 성공적으로 등록되었습니다.")
				.timestamp(entity.getCreatedAt())
				.intentId(entity.getId())
				.memberId(entity.getMemberId())
				.giveCourseNo(entity.getGiveCourseNo())
				.wantCourseNo(entity.getWantCourseNo())
				.isDeleted(entity.isDeleted())
				.build();
	}
}
