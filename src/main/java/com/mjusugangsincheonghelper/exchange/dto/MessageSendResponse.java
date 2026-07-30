package com.mjusugangsincheonghelper.exchange.dto;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MessageSendResponse {

	private Long messageId;
	private Long roomId;
	private Long senderId;
	private String messageType;
	private String content;
	private Instant createdAt;

	public static MessageSendResponse from(ExchangeRoomMessageEntity entity) {
		return MessageSendResponse.builder()
				.messageId(entity.getId())
				.roomId(entity.getRoomId())
				.senderId(entity.getMemberId())
				.messageType(entity.getMessageType())
				.content(entity.getContent())
				.createdAt(entity.getCreatedAt())
				.build();
	}
}
