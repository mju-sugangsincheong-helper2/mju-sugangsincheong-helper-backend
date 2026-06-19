package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "single_game")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SingleGameEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false)
	private int tTotal;

	@Column(nullable = false)
	private int tEnterMain;

	@Column(nullable = false)
	private boolean isCompleted;

	@Column(nullable = false)
	private int totalCourses;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private List<SingleGameDetailEntity> details = new ArrayList<>();

	@Builder
	public SingleGameEntity(Long memberId, int tTotal, int tEnterMain, boolean isCompleted, int totalCourses) {
		this.memberId = memberId;
		this.tTotal = tTotal;
		this.tEnterMain = tEnterMain;
		this.isCompleted = isCompleted;
		this.totalCourses = totalCourses;
	}
}
