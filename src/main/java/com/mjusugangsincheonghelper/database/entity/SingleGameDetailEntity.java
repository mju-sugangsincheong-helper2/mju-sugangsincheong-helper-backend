package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "single_game_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(SingleGameDetailId.class)
public class SingleGameDetailEntity {

	@Id
	@Column(name = "game_id")
	private Long gameId;

	@Id
	private int sequence;

	@Column(nullable = false)
	private int tClickCourse;

	@Column(nullable = false)
	private int tClickYes;

	@Column(nullable = false)
	private int tClickOk;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "game_id", insertable = false, updatable = false)
	private SingleGameEntity game;

	@Builder
	public SingleGameDetailEntity(Long gameId, int sequence, int tClickCourse, int tClickYes, int tClickOk) {
		this.gameId = gameId;
		this.sequence = sequence;
		this.tClickCourse = tClickCourse;
		this.tClickYes = tClickYes;
		this.tClickOk = tClickOk;
	}
}
