package com.mjusugangsincheonghelper.example.dto;

import com.mjusugangsincheonghelper.database.entity.ExampleEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ExamplePageItem {

	private final Long id;
	private final String title;
	private final boolean active;

	public static ExamplePageItem from(ExampleEntity entity) {
		return ExamplePageItem.builder()
				.id(entity.getId())
				.title(entity.getTitle())
				.active(entity.isActive())
				.build();
	}
}
