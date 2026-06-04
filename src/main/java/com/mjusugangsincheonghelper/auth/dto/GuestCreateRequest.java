package com.mjusugangsincheonghelper.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestCreateRequest {

	private String fcmToken;
	private DeviceInfo device;
}
