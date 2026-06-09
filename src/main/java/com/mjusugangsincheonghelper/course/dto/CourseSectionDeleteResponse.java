package com.mjusugangsincheonghelper.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class CourseSectionDeleteResponse {

	private final long deletedCount;
}
