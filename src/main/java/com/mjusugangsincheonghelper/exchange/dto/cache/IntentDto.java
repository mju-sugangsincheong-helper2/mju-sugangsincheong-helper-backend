package com.mjusugangsincheonghelper.exchange.dto.cache;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long intentId;
	private String giveCourseNo;
	private String wantCourseNo;
	private boolean isDeleted;
	private Instant createdAt;

	public static IntentDto from(ExchangeIntentEntity entity) {
		return IntentDto.builder()
				.intentId(entity.getId())
				.giveCourseNo(entity.getGiveCourseNo())
				.wantCourseNo(entity.getWantCourseNo())
				.isDeleted(entity.isDeleted())
				.createdAt(entity.getCreatedAt())
				.build();
	}
}
