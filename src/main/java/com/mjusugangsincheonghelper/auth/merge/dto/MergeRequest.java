package com.mjusugangsincheonghelper.auth.merge.dto;

import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;

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
	private DeviceInfo device;
}
