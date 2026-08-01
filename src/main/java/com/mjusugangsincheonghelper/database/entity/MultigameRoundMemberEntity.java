package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "multigame_round_member",
		uniqueConstraints = @UniqueConstraint(columnNames = {"start_time", "member_id", "subject_id"}),
		indexes = @Index(name = "idx_multigame_round_member_member_id", columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MultigameRoundMemberEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "start_time", nullable = false, length = 14)
	private String startTime;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "subject_id", nullable = false)
	private int subjectId;

	@Column(nullable = false, length = 20)
	private String status;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public MultigameRoundMemberEntity(String startTime, Long memberId, int subjectId, String status) {
		this.startTime = startTime;
		this.memberId = memberId;
		this.subjectId = subjectId;
		this.status = status;
	}

	public void update(int subjectId, String status) {
		this.subjectId = subjectId;
		this.status = status;
	}
}
