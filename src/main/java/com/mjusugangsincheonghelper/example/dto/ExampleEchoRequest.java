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
public class ExampleEchoRequest {

	@NotBlank(message = "message는 필수입니다.")
	@Size(max = 100, message = "message는 100자 이하여야 합니다.")
	private String message;
}
