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
public class RoomCacheDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long roomId;
	private boolean isActive;
	private boolean isOn;
	private int unreadCount;
	private String lastMessageContent;
	private Instant lastMessageAt;
}
