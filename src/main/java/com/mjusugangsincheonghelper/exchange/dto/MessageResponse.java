package com.mjusugangsincheonghelper.exchange.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
	private List<ParticipantItem> participants;
	private List<MessageItem> messages;
	private Long nextBeforeMessageId;
	private boolean hasNext;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(name = "MessageResponseParticipantItem")
	public static class ParticipantItem {
		private Long memberId;
		private String name;
		private String department;
		private Long intentId;
		private String giveCourseNo;
		private String wantCourseNo;
		private boolean deleted;
		private boolean on;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(name = "MessageResponseMessageItem")
	public static class MessageItem {
		private Long messageId;
		private Long senderId;
		private String messageType;
		private String content;
		private Instant createdAt;
	}
}
