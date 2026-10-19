package com.mjusugangsincheonghelper.singlegame.dto.cache;

import com.mjusugangsincheonghelper.singlegame.service.SegmentMetric;
import java.io.Serializable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구간별 모집단 분위수표. 전체/학과 공용 타입이며 캐시 키만 다르다.
 *
 * <p>기존 {@code PopulationTable(int[16])}의 매직인덱스를 제거한 대체품.
 * {@code sequence → (지표 → QuantileBand)} 구조로 지표 접근을 명시한다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SequenceQuantileTable implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 구간시도별 분포. key=sequence, value=지표별 분위수묶음 */
	private Map<Integer, EnumMap<SegmentMetric, QuantileBand>> segmentQuantiles;

	/** 진입 지연(t_enter_main) 분포. 모집단 없음(null 가능) */
	private QuantileBand enterMain;

	public static SequenceQuantileTable empty() {
		return SequenceQuantileTable.builder()
				.segmentQuantiles(Map.of())
				.enterMain(null)
				.build();
	}

	/** 해당 sequence·지표의 분위수묶음. 없으면 빈 밴드 */
	public QuantileBand quantileOf(int sequence, SegmentMetric metric) {
		if (segmentQuantiles == null) {
			return QuantileBand.empty();
		}
		EnumMap<SegmentMetric, QuantileBand> perMetric = segmentQuantiles.get(sequence);
		if (perMetric == null) {
			return QuantileBand.empty();
		}
		QuantileBand band = perMetric.get(metric);
		return band != null ? band : QuantileBand.empty();
	}

	/** 캐시 역직렬화 등에서 일반 Map으로 들어온 값을 EnumMap으로 정규화 */
	public static Map<Integer, EnumMap<SegmentMetric, QuantileBand>> copyOf(
			Map<Integer, EnumMap<SegmentMetric, QuantileBand>> source) {
		if (source == null) {
			return Map.of();
		}
		Map<Integer, EnumMap<SegmentMetric, QuantileBand>> copy = new HashMap<>();
		source.forEach((seq, perMetric) -> copy.put(seq, new EnumMap<>(perMetric)));
		return copy;
	}
}
