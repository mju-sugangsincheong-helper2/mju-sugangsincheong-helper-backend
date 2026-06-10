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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "exchange_intent")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ExchangeIntentEntity.ExchangeIntentId.class)
@EntityListeners(AuditingEntityListener.class)
public class ExchangeIntentEntity {

	private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

	@Id
	@Column(length = 6)
	private String term;

	@Id
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false, length = 20)
	private String giveCourseNo;

	@Column(nullable = false, length = 20)
	private String wantCourseNo;

	@Column(nullable = false)
	private boolean isDeleted;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	public ExchangeIntentEntity(String term, Long memberId, String giveCourseNo, String wantCourseNo) {
		this.term = term;
		this.memberId = memberId;
		this.giveCourseNo = giveCourseNo;
		this.wantCourseNo = wantCourseNo;
		this.isDeleted = false;
	}

	@PrePersist
	public void prePersist() {
		if (this.id == null) {
			this.id = ID_GENERATOR.getAndIncrement();
		}
	}

	public void delete() {
		this.isDeleted = true;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ExchangeIntentId implements Serializable {
		private String term;
		private Long id;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExchangeIntentId that = (ExchangeIntentId) o;
			return Objects.equals(term, that.term) && Objects.equals(id, that.id);
		}

		@Override
		public int hashCode() {
			return Objects.hash(term, id);
		}
	}
}
