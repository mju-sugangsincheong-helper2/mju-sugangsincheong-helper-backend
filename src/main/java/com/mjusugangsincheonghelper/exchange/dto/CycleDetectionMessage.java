package com.mjusugangsincheonghelper.exchange.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleDetectionMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	private String term;
	private Long intentId;
	private Long memberId;
	private String giveCourseNo;
	private String wantCourseNo;
}
