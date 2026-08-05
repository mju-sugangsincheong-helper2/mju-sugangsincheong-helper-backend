package com.mjusugangsincheonghelper.notice.dto;

import com.mjusugangsincheonghelper.database.entity.NoticeEntity;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NoticeResponse {

	private Long id;
	private String type;
	private String title;
	private String content;
	private Instant createdAt;

	public static NoticeResponse from(NoticeEntity entity) {
		return NoticeResponse.builder()
				.id(entity.getId())
				.type(entity.getType())
				.title(entity.getTitle())
				.content(entity.getContent())
				.createdAt(entity.getCreatedAt())
				.build();
	}
}