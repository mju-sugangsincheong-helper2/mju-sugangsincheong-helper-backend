package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "latency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class LatencyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id")
	private Long memberId;

	@Column(name = "median_ms", nullable = false)
	private Double medianMs;

	@Column(name = "max_ms", nullable = false)
	private Double maxMs;

	@Column(name = "min_ms", nullable = false)
	private Double minMs;

	@Column(name = "std_dev_ms", nullable = false)
	private Double stdDevMs;

	@Column(name = "sample_count", nullable = false)
	private Integer sampleCount;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String samples;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public LatencyEntity(Long memberId, Double medianMs, Double maxMs, Double minMs,
	                     Double stdDevMs, Integer sampleCount, String samples) {
		this.memberId = memberId;
		this.medianMs = medianMs;
		this.maxMs = maxMs;
		this.minMs = minMs;
		this.stdDevMs = stdDevMs;
		this.sampleCount = sampleCount;
		this.samples = samples;
	}
}
