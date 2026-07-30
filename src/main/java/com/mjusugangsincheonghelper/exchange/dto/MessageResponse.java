package com.mjusugangsincheonghelper.exchange.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

	private Long roomId;
	private List<MessageItem> messages;
	private Long nextBeforeMessageId;
	private boolean hasNext;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MessageItem {
		private Long messageId;
		private Long senderId;
		private String messageType;
		private String content;
		private Instant createdAt;
	}
}
