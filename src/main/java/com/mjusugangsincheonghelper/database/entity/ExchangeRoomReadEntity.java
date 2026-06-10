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
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "exchange_room_read")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ExchangeRoomReadEntity.ExchangeRoomReadId.class)
public class ExchangeRoomReadEntity {

	@Id
	@Column(length = 6)
	private String term;

	@Id
	private Long roomId;

	@Id
	private Long memberId;

	@Column(nullable = false)
	private Long lastReadMessageId;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	public ExchangeRoomReadEntity(String term, Long roomId, Long memberId) {
		this.term = term;
		this.roomId = roomId;
		this.memberId = memberId;
		this.lastReadMessageId = 0L;
	}

	public void updateLastReadMessageId(Long lastReadMessageId) {
		this.lastReadMessageId = lastReadMessageId;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ExchangeRoomReadId implements Serializable {
		private String term;
		private Long roomId;
		private Long memberId;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExchangeRoomReadId that = (ExchangeRoomReadId) o;
			return Objects.equals(term, that.term) && Objects.equals(roomId, that.roomId) && Objects.equals(memberId, that.memberId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(term, roomId, memberId);
		}
	}
}
