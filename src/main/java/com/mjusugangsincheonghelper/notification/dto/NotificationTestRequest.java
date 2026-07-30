package com.mjusugangsincheonghelper.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTestRequest {

	@NotBlank(message = "title은 필수입니다.")
	private String title;

	@NotBlank(message = "body는 필수입니다.")
	private String body;
}
