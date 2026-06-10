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
@Table(name = "exchange_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ExchangeMessageEntity.ExchangeMessageId.class)
@EntityListeners(AuditingEntityListener.class)
public class ExchangeMessageEntity {

	private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

	@Id
	@Column(length = 6)
	private String term;

	@Id
	private Long id;

	@Column(nullable = false)
	private Long roomId;

	@Column(nullable = false)
	private Long senderId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public ExchangeMessageEntity(String term, Long roomId, Long senderId, String content) {
		this.term = term;
		this.roomId = roomId;
		this.senderId = senderId;
		this.content = content;
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
	public static class ExchangeMessageId implements Serializable {
		private String term;
		private Long id;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExchangeMessageId that = (ExchangeMessageId) o;
			return Objects.equals(term, that.term) && Objects.equals(id, that.id);
		}

		@Override
		public int hashCode() {
			return Objects.hash(term, id);
		}
	}
}
