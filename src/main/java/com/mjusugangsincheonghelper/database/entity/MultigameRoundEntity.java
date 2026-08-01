package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "multigame_round")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MultigameRoundEntity {

	@Id
	@Column(name = "start_time", nullable = false, length = 14)
	private String startTime;

	@Column(name = "participant_count", nullable = false)
	private int participantCount;

	@Column(nullable = false)
	private int capacity;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public MultigameRoundEntity(String startTime, int participantCount, int capacity) {
		this.startTime = startTime;
		this.participantCount = participantCount;
		this.capacity = capacity;
	}

	public void update(int participantCount, int capacity) {
		this.participantCount = participantCount;
		this.capacity = capacity;
	}
}
