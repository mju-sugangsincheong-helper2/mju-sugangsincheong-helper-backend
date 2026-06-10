package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "exchange_room", uniqueConstraints = {
		@UniqueConstraint(name = "uk_exchange_room_cycle_hash", columnNames = {"term", "cycle_hash"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ExchangeRoomEntity.ExchangeRoomId.class)
@EntityListeners(AuditingEntityListener.class)
public class ExchangeRoomEntity {

	private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

	@Id
	@Column(length = 6)
	private String term;

	@Id
	private Long id;

	@Column(nullable = false, length = 64)
	private String cycleHash;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	public ExchangeRoomEntity(String term, String cycleHash) {
		this.term = term;
		this.cycleHash = cycleHash;
	}

	@PrePersist
	public void prePersist() {
		if (this.id == null) {
			this.id = ID_GENERATOR.getAndIncrement();
		}
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ExchangeRoomId implements Serializable {
		private String term;
		private Long id;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExchangeRoomId that = (ExchangeRoomId) o;
			return Objects.equals(term, that.term) && Objects.equals(id, that.id);
		}

		@Override
		public int hashCode() {
			return Objects.hash(term, id);
		}
	}
}
