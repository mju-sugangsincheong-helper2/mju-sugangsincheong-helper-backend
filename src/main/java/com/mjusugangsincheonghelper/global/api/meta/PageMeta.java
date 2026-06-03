package com.mjusugangsincheonghelper.global.api.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
@AllArgsConstructor
public class PageMeta {

	private final int pageNumber;
	private final int pageSize;
	private final long totalElements;
	private final int totalPages;
	private final boolean hasNext;
	private final boolean hasPrevious;

	public static PageMeta from(Page<?> page) {
		return PageMeta.builder()
				.pageNumber(page.getNumber())
				.pageSize(page.getSize())
				.totalElements(page.getTotalElements())
				.totalPages(page.getTotalPages())
				.hasNext(page.hasNext())
				.hasPrevious(page.hasPrevious())
				.build();
	}
}
