package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@DynamicUpdate
@Table(name = "multigame_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MultigameResultEntity {

	@Id
	@Column(length = 14)
	private String startTime;

	@Column(nullable = false)
	private int participantCount;

	@Column(nullable = false)
	private int capacity;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant finalizedAt;

	@Builder
	public MultigameResultEntity(String startTime, int participantCount, int capacity, Instant finalizedAt) {
		this.startTime = startTime;
		this.participantCount = participantCount;
		this.capacity = capacity;
		this.finalizedAt = finalizedAt;
	}

	public void finalizeResult(Instant finalizedAt) {
		this.finalizedAt = finalizedAt;
	}
}
