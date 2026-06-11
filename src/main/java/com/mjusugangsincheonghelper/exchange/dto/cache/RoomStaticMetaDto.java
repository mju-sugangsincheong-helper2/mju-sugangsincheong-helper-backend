package com.mjusugangsincheonghelper.exchange.dto.cache;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStaticMetaDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long roomId;
	private int totalParticipants;
	private List<CycleDetailDto> cycleDetails;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CycleDetailDto implements Serializable {

		private static final long serialVersionUID = 1L;

		private Long memberId;
		private String giveCourseNo;
		private String wantCourseNo;
	}
}
