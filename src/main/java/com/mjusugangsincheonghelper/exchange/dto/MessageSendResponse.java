package com.mjusugangsincheonghelper.exchange.dto;

import com.mjusugangsincheonghelper.database.entity.ExchangeMessageEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MessageSendResponse {

	private final String message;
	private final Instant timestamp;
	private final Long messageId;
	private final Long roomId;
	private final Long senderId;
	private final String content;
	private final Instant createdAt;

	public static MessageSendResponse from(ExchangeMessageEntity entity) {
		return MessageSendResponse.builder()
				.message("메시지가 전송되었습니다.")
				.timestamp(entity.getCreatedAt())
				.messageId(entity.getId())
				.roomId(entity.getRoomId())
				.senderId(entity.getSenderId())
				.content(entity.getContent())
				.createdAt(entity.getCreatedAt())
				.build();
	}
}
