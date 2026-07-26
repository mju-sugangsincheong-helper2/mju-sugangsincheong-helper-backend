package com.mjusugangsincheonghelper.auth.guest.dto;

import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestCreateRequest {

	private DeviceInfo device;
}
