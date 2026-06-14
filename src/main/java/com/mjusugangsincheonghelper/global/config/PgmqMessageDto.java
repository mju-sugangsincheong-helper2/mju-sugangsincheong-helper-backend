package com.mjusugangsincheonghelper.global.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PgmqMessageDto {

	private Long msgId;
	private int readCt;
	private String message;
}
