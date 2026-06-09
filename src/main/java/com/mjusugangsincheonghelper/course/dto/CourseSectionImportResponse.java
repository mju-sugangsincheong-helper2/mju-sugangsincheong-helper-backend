package com.mjusugangsincheonghelper.course.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class CourseSectionImportResponse {

	private final int importedCount;
	private final List<String> terms;
}
