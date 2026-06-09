package com.mjusugangsincheonghelper.course.dto;

import com.mjusugangsincheonghelper.database.entity.CourseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class CourseSectionResponse {

	private final String coursecls;
	private final String term;
	private final String curinum;
	private final String curinm;
	private final String profnm;
	private final String lecttime;
	private final String lecperiod;
	private final String cdtnum;
	private final String cdttime;
	private final String takelim;
	private final String listennow;

	public static CourseSectionResponse from(CourseEntity entity) {
		return CourseSectionResponse.builder()
				.coursecls(entity.getCoursecls())
				.term(entity.getTerm())
				.curinum(entity.getCurinum())
				.curinm(entity.getCurinm())
				.profnm(entity.getProfnm())
				.lecttime(entity.getLecttime())
				.lecperiod(entity.getLecperiod())
				.cdtnum(entity.getCdtnum())
				.cdttime(entity.getCdttime())
				.takelim(entity.getTakelim())
				.listennow(entity.getListennow())
				.build();
	}
}
