package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "exchange_room_intent")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ExchangeRoomIntentEntity.ExchangeRoomIntentId.class)
@EntityListeners(AuditingEntityListener.class)
public class ExchangeRoomIntentEntity {

	@Id
	@Column(length = 10)
	private String term;

	@Id
	private Long roomId;

	@Id
	private Long intentId;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "is_deleted", nullable = false)
	private boolean isDeleted;

	@Column(name = "is_on", nullable = false)
	private boolean isOn;

	@CreatedDate
	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	@Builder
	public ExchangeRoomIntentEntity(String term, Long roomId, Long intentId, Long memberId) {
		this.term = term;
		this.roomId = roomId;
		this.intentId = intentId;
		this.memberId = memberId;
		this.isDeleted = false;
		this.isOn = true;
	}

	public void markDeleted() {
		this.isDeleted = true;
		this.isOn = false;
	}

	public void toggle(boolean isOn) {
		this.isOn = isOn;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ExchangeRoomIntentId implements Serializable {
		private String term;
		private Long roomId;
		private Long intentId;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExchangeRoomIntentId that = (ExchangeRoomIntentId) o;
			return Objects.equals(term, that.term) && Objects.equals(roomId, that.roomId) && Objects.equals(intentId, that.intentId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(term, roomId, intentId);
		}
	}
}
