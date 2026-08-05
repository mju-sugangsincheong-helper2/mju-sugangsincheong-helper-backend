package com.mjusugangsincheonghelper.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공지 생성/수정 공용 요청 (둘 다 동일 필드) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeRequest {

	@NotBlank
	@Pattern(regexp = "critical|update|general", message = "type은 critical|update|general 중 하나여야 합니다.")
	private String type;

	@NotBlank
	@Size(max = 200)
	private String title;

	@NotBlank
	private String content;
}