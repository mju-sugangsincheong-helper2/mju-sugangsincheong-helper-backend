package com.mjusugangsincheonghelper.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeRequest {

	@NotBlank
	private String mergeTicket;
	private String fcmToken;
	private DeviceInfo device;
}
