package com.mjusugangsincheonghelper.exchange.dto;

import com.mjusugangsincheonghelper.database.entity.ExchangeMessageEntity;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MessageResponse {

	private final String message;
	private final Instant timestamp;
	private final Long roomId;
	private final List<MessageItem> messages;
	private final Long nextLastMessageId;
	private final boolean hasNext;

	@Getter
	@Builder
	@AllArgsConstructor
	public static class MessageItem {
		private final Long messageId;
		private final Long senderId;
		private final String content;
		private final Instant createdAt;

		public static MessageItem from(ExchangeMessageEntity entity) {
			return MessageItem.builder()
					.messageId(entity.getId())
					.senderId(entity.getSenderId())
					.content(entity.getContent())
					.createdAt(entity.getCreatedAt())
					.build();
		}
	}
}
