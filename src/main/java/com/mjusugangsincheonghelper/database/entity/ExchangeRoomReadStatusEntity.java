package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.EntityListeners;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "exchange_room_read_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId.class)
@EntityListeners(AuditingEntityListener.class)
public class ExchangeRoomReadStatusEntity {

	@Id
	@Column(length = 10)
	private String term;

	@Id
	private Long roomId;

	@Id
	private Long memberId;

	@Column(name = "intent_id", nullable = false)
	private Long intentId;

	@Column(name = "last_read_message_id", nullable = false)
	private Long lastReadMessageId;

	@LastModifiedDate
	@Column(name = "last_read_at", nullable = false)
	private Instant lastReadAt;

	@Builder
	public ExchangeRoomReadStatusEntity(String term, Long roomId, Long memberId, Long intentId) {
		this.term = term;
		this.roomId = roomId;
		this.memberId = memberId;
		this.intentId = intentId;
		this.lastReadMessageId = 0L;
		this.lastReadAt = Instant.now();
	}

	public void updateLastReadMessageId(Long lastReadMessageId) {
		this.lastReadMessageId = lastReadMessageId;
		this.lastReadAt = Instant.now();
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ExchangeRoomReadStatusId implements Serializable {
		private String term;
		private Long roomId;
		private Long memberId;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExchangeRoomReadStatusId that = (ExchangeRoomReadStatusId) o;
			return Objects.equals(term, that.term) && Objects.equals(roomId, that.roomId) && Objects.equals(memberId, that.memberId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(term, roomId, memberId);
		}
	}
}
