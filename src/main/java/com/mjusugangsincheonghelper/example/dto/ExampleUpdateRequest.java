package com.mjusugangsincheonghelper.example.dto;

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
public class ExampleUpdateRequest {

	@NotBlank(message = "title은 필수입니다.")
	@Size(max = 200, message = "title은 200자 이하여야 합니다.")
	private String title;

	@Size(max = 5000, message = "content는 5000자 이하여야 합니다.")
	private String content;
}
