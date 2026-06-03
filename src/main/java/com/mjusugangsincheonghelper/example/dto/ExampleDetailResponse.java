package com.mjusugangsincheonghelper.example.dto;

import com.mjusugangsincheonghelper.database.entity.ExampleEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ExampleDetailResponse {

	private final Long id;
	private final String title;
	private final String content;
	private final boolean active;
	private final Instant createdAt;
	private final Instant updatedAt;

	public static ExampleDetailResponse from(ExampleEntity entity) {
		return ExampleDetailResponse.builder()
				.id(entity.getId())
				.title(entity.getTitle())
				.content(entity.getContent())
				.active(entity.isActive())
				.createdAt(entity.getCreatedAt())
				.updatedAt(entity.getUpdatedAt())
				.build();
	}
}
