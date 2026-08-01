package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "multigame_round_log",
		indexes = @Index(name = "idx_multigame_round_log_member_id", columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MultigameRoundLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "start_time", nullable = false, length = 14)
	private String startTime;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "subject_id", nullable = false)
	private int subjectId;

	@Column(name = "attempt_status", nullable = false, length = 20)
	private String attemptStatus;

	@Column(name = "attempt_seq", nullable = false)
	private long attemptSeq;

	@Column(name = "current_limit", nullable = false)
	private int currentLimit;

	@Column(name = "attempted_at", nullable = false)
	private Instant attemptedAt;

	@Builder
	public MultigameRoundLogEntity(String startTime, Long memberId, int subjectId, String attemptStatus,
			long attemptSeq, int currentLimit, Instant attemptedAt) {
		this.startTime = startTime;
		this.memberId = memberId;
		this.subjectId = subjectId;
		this.attemptStatus = attemptStatus;
		this.attemptSeq = attemptSeq;
		this.currentLimit = currentLimit;
		this.attemptedAt = attemptedAt;
	}
}
