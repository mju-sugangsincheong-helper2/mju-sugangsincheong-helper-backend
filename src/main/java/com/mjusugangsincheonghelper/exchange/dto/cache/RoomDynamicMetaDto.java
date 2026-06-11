package com.mjusugangsincheonghelper.exchange.dto.cache;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDynamicMetaDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private String lastMessage;
	private Instant lastMessageAt;
}
