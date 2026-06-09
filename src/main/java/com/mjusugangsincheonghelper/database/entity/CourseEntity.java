package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(CourseEntity.CourseId.class)
@EntityListeners(AuditingEntityListener.class)
public class CourseEntity {

	@Id
	@Column(length = 10)
	private String coursecls;

	@Id
	@Column(length = 6)
	private String term;

	@Column(length = 10)
	private String campusdiv;

	@Column(length = 10)
	private String classdiv;

	@Column(length = 10)
	private String gbn;

	@Column(length = 10)
	private String curigbn;

	@Column(length = 10)
	private String comyear;

	@Column(length = 50)
	private String curinum;

	@Column(length = 50)
	private String curinum2;

	@Column(length = 200)
	private String curinm;

	@Column(length = 50)
	private String groupcd;

	@Column(length = 10)
	private String cdtnum;

	@Column(length = 10)
	private String cdttime;

	@Column(length = 10)
	private String takelim;

	@Column(length = 10)
	private String listennow;

	@Column(length = 50)
	private String deptcd;

	@Column(length = 100)
	private String deptnm;

	@Column(length = 50)
	private String profid;

	@Column(length = 100)
	private String profnm;

	@Column(length = 10)
	private String largetp;

	@Column(length = 10)
	private String smalltp;

	@Column(length = 10)
	private String abotp;

	@Column(columnDefinition = "TEXT")
	private String lecttime;

	@Column(length = 10)
	private String dislevel;

	@Column(columnDefinition = "TEXT")
	private String curicontent;

	@Column(length = 10)
	private String bagcnt;

	@Column(columnDefinition = "TEXT")
	private String dbtimelist;

	@Column(length = 10)
	private String sugyn;

	@Column(length = 50)
	private String addtime;

	@Column(length = 10)
	private String internetyn;

	@Column(length = 10)
	private String flexyn;

	@Column(length = 10)
	private String classtype;

	@Column(columnDefinition = "TEXT")
	private String lecperiod;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	public CourseEntity(
			String coursecls, String term,
			String campusdiv, String classdiv, String gbn, String curigbn,
			String comyear, String curinum, String curinum2,
			String curinm, String groupcd, String cdtnum, String cdttime,
			String takelim, String listennow, String deptcd, String deptnm,
			String profid, String profnm, String largetp, String smalltp,
			String abotp, String lecttime, String dislevel, String curicontent,
			String bagcnt, String dbtimelist, String sugyn, String addtime,
			String internetyn, String flexyn, String classtype, String lecperiod
	) {
		this.coursecls = coursecls;
		this.term = term;
		this.campusdiv = campusdiv;
		this.classdiv = classdiv;
		this.gbn = gbn;
		this.curigbn = curigbn;
		this.comyear = comyear;
		this.curinum = curinum;
		this.curinum2 = curinum2;
		this.curinm = curinm;
		this.groupcd = groupcd;
		this.cdtnum = cdtnum;
		this.cdttime = cdttime;
		this.takelim = takelim;
		this.listennow = listennow;
		this.deptcd = deptcd;
		this.deptnm = deptnm;
		this.profid = profid;
		this.profnm = profnm;
		this.largetp = largetp;
		this.smalltp = smalltp;
		this.abotp = abotp;
		this.lecttime = lecttime;
		this.dislevel = dislevel;
		this.curicontent = curicontent;
		this.bagcnt = bagcnt;
		this.dbtimelist = dbtimelist;
		this.sugyn = sugyn;
		this.addtime = addtime;
		this.internetyn = internetyn;
		this.flexyn = flexyn;
		this.classtype = classtype;
		this.lecperiod = lecperiod;
	}

	@NoArgsConstructor
	@AllArgsConstructor
	public static class CourseId implements Serializable {
		private String coursecls;
		private String term;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			CourseId courseId = (CourseId) o;
			return Objects.equals(coursecls, courseId.coursecls) && Objects.equals(term, courseId.term);
		}

		@Override
		public int hashCode() {
			return Objects.hash(coursecls, term);
		}
	}
}
