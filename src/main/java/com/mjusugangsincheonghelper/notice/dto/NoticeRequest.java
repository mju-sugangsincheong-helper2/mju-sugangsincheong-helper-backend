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

	/**
	 * 공지 생성 시에만 사용: true면 전체 사용자에게 FCM 푸시를 발송하고,
	 * null/false면 공지 저장만 한다. 수정(update)에서는 무시된다.
	 */
	private Boolean broadcast;
}