package com.mjusugangsincheonghelper.exchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentCreateRequest {

	@NotBlank(message = "giveCourseNo는 필수입니다.")
	@Size(max = 20, message = "giveCourseNo는 20자 이하여야 합니다.")
	private String giveCourseNo;

	@NotBlank(message = "wantCourseNo는 필수입니다.")
	@Size(max = 20, message = "wantCourseNo는 20자 이하여야 합니다.")
	private String wantCourseNo;
}
