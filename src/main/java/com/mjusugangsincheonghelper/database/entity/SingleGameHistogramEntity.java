package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 싱글게임 버킷 히스토그램 사전집계. 완료된 게임 1판마다 구간 지표 4종 x sequence +
 * 진입 TOTAL + 피드백 4종을 5ms 버킷 카운터로 누적한다.
 *
 * <p>scope: 0=전체(구간+진입), 1=학과별(구간+진입), 2=피드백(전체).
 * sequence: 구간표 1..N, 진입/피드백 0. metric: 1=AIM,2=CONFIRM,3=COMPLETE,4=TOTAL.
 * bucketMs: 5ms 버킷 하한. scope 0/2의 department는 ""이다.</p>
 */
@Entity
@Table(name = "single_game_histogram")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(SingleGameHistogramId.class)
public class SingleGameHistogramEntity {

	/** scope 상수. */
	public static final short SCOPE_GLOBAL = 0;
	public static final short SCOPE_DEPARTMENT = 1;
	public static final short SCOPE_FEEDBACK = 2;

	/** 진입/피드백 행의 sequence 값. */
	public static final int SEQUENCE_SUMMARY = 0;

	@Id
	private int totalCourses;

	@Id
	private short scope;

	@Id
	@Column(nullable = false)
	private String department;

	@Id
	private int sequence;

	@Id
	private short metric;

	@Id
	private int bucketMs;

	@Column(nullable = false)
	private long cnt;

	@Builder
	public SingleGameHistogramEntity(int totalCourses, short scope, String department,
			int sequence, short metric, int bucketMs, long cnt) {
		this.totalCourses = totalCourses;
		this.scope = scope;
		this.department = department;
		this.sequence = sequence;
		this.metric = metric;
		this.bucketMs = bucketMs;
		this.cnt = cnt;
	}
}
