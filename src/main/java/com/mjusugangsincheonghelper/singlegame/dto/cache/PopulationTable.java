package com.mjusugangsincheonghelper.singlegame.dto.cache;

import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구간별 모집단 분포표. 전체/학과 공용 타입이며 캐시 키만 다르다.
 *
 * <p>값은 전부 int(ms)이다. 응답의 PopulationStats가 int이므로 손실이 없고,
 * double[] 대비 JSON 직렬화 비용이 무시 가능한 수준으로 줄어든다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopulationTable implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 시퀀스별 분포. 값 int[16] = cc/cy/cok/total × p10/p30/p50/p70 */
	private Map<Integer, int[]> seqBands;

	/** 진입 시간 분포 int[4] = p10/p30/p50/p70. 모집단 없음(null 가능) */
	private int[] enterMain;

	public static PopulationTable empty() {
		return PopulationTable.builder()
				.seqBands(Map.of())
				.enterMain(null)
				.build();
	}
}
