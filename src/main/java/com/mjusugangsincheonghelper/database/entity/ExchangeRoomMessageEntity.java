package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "exchange_room_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ExchangeRoomMessageEntity.ExchangeRoomMessageId.class)
@EntityListeners(AuditingEntityListener.class)
public class ExchangeRoomMessageEntity {

	private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

	@Id
	@Column(length = 10)
	private String term;

	@Id
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "member_id", nullable = true)
	private Long memberId;

	@Column(name = "intent_id", nullable = true)
	private Long intentId;

	@Column(name = "message_type", nullable = false, length = 10)
	private String messageType;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public ExchangeRoomMessageEntity(String term, Long roomId, Long memberId, Long intentId, String messageType, String content) {
		this.term = term;
		this.roomId = roomId;
		this.memberId = memberId;
		this.intentId = intentId;
		this.messageType = messageType != null ? messageType : "TALK";
		this.content = content;
	}

	@PrePersist
	public void prePersist() {
		if (this.id == null) {
			this.id = System.currentTimeMillis() * 1000L + (ID_GENERATOR.getAndIncrement() % 1000L);
		}
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ExchangeRoomMessageId implements Serializable {
		private String term;
		private Long id;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExchangeRoomMessageId that = (ExchangeRoomMessageId) o;
			return Objects.equals(term, that.term) && Objects.equals(id, that.id);
		}

		@Override
		public int hashCode() {
			return Objects.hash(term, id);
		}
	}
}
