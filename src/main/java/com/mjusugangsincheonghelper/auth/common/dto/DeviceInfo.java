package com.mjusugangsincheonghelper.auth.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfo {

	private String name;
	private String version;
	private String layout;
	private String prerelease;
	private String os;
	private String manufacturer;
	private String product;
	private String description;
	private String ua;
}
