package com.mjusugangsincheonghelper.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSectionImportRequest {

	@NotBlank
	private String curiyear;

	@NotBlank
	private String curismt;

	@Size(max = 10)
	private String campusdiv;

	@Size(max = 10)
	private String classdiv;

	@Size(max = 10)
	private String gbn;

	@Size(max = 10)
	private String curigbn;

	@Size(max = 10)
	private String comyear;

	@NotBlank
	@Size(max = 50)
	private String curinum;

	@NotBlank
	@Size(max = 10)
	private String coursecls;

	@Size(max = 50)
	private String curinum2;

	@NotBlank
	@Size(max = 200)
	private String curinm;

	@Size(max = 50)
	private String groupcd;

	@Size(max = 10)
	private String cdtnum;

	@Size(max = 10)
	private String cdttime;

	@Size(max = 10)
	private String takelim;

	@Size(max = 10)
	private String listennow;

	@Size(max = 50)
	private String deptcd;

	@Size(max = 100)
	private String deptnm;

	@Size(max = 50)
	private String profid;

	@Size(max = 100)
	private String profnm;

	@Size(max = 10)
	private String largetp;

	@Size(max = 10)
	private String smalltp;

	@Size(max = 10)
	private String abotp;

	private String lecttime;

	@Size(max = 10)
	private String dislevel;

	private String curicontent;

	@Size(max = 10)
	private String bagcnt;

	private String dbtimelist;

	@Size(max = 10)
	private String sugyn;

	@Size(max = 50)
	private String addtime;

	@Size(max = 10)
	private String internetyn;

	@Size(max = 10)
	private String flexyn;

	@Size(max = 10)
	private String classtype;

	private String lecperiod;
}
