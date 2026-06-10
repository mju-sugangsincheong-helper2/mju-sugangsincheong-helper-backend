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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "exchange_room_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ExchangeRoomMemberEntity.ExchangeRoomMemberId.class)
@EntityListeners(AuditingEntityListener.class)
public class ExchangeRoomMemberEntity {

	@Id
	@Column(length = 6)
	private String term;

	@Id
	private Long roomId;

	@Id
	private Long memberId;

	@Column(nullable = false)
	private Long intentId;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant joinedAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	public ExchangeRoomMemberEntity(String term, Long roomId, Long memberId, Long intentId) {
		this.term = term;
		this.roomId = roomId;
		this.memberId = memberId;
		this.intentId = intentId;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ExchangeRoomMemberId implements Serializable {
		private String term;
		private Long roomId;
		private Long memberId;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExchangeRoomMemberId that = (ExchangeRoomMemberId) o;
			return Objects.equals(term, that.term) && Objects.equals(roomId, that.roomId) && Objects.equals(memberId, that.memberId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(term, roomId, memberId);
		}
	}
}
