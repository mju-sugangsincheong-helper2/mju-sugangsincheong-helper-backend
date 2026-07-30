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
public class MessageSendRequest {

	@NotBlank(message = "content는 필수입니다.")
	@Size(max = 30000, message = "content는 30000자 이하여야 합니다.")
	private String content;
}
