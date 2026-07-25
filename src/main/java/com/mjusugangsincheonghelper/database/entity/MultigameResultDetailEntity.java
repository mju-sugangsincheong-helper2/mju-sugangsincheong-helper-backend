package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "multigame_result_detail", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"start_time", "member_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MultigameResultDetailEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 14)
	private String startTime;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false)
	private int subjectId;

	@Column(nullable = false, length = 20)
	private String status;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public MultigameResultDetailEntity(String startTime, Long memberId, int subjectId, String status) {
		this.startTime = startTime;
		this.memberId = memberId;
		this.subjectId = subjectId;
		this.status = status;
	}
}
