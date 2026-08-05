package com.mjusugangsincheonghelper.multigame.result.service;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundLogEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundMemberEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundLogRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundMemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundRecordResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.RoundDetailResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.RoundSummaryResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoundResultService {

	private final MultigameRoundRepository roundRepository;
	private final MultigameRoundMemberRepository memberRepository;
	private final MultigameRoundLogRepository logRepository;

	public Page<RoundSummaryResponse> rounds(int page, int size) {
		return roundRepository.findAllByOrderByStartTimeDesc(PageRequest.of(page, size))
				.map(RoundSummaryResponse::from);
	}

	/**
	 * 내 참여 기록을 라운드 단위로 페이징 조회한다.
	 * 페이지네이션 단위는 라운드이며, 한 라운드의 참여자 수(participantCount)와
	 * 내 성공 과목 수(successCount)만 노출한다.
	 */
	public Page<MyRoundRecordResponse> myRecords(long memberId, int page, int size) {
		Page<String> startTimes = memberRepository.findDistinctStartTimesByMemberId(memberId, PageRequest.of(page, size));
		if (startTimes.isEmpty()) {
			return Page.empty(startTimes.getPageable());
		}
		Map<String, MultigameRoundEntity> roundsByStartTime = roundRepository.findAllById(startTimes.getContent()).stream()
				.collect(Collectors.toMap(MultigameRoundEntity::getStartTime, round -> round));
		Map<String, List<MultigameRoundMemberEntity>> rowsByStartTime = memberRepository
				.findByStartTimeInAndMemberIdOrderByStartTimeDescSubjectIdAsc(startTimes.getContent(), memberId)
				.stream()
				.collect(Collectors.groupingBy(MultigameRoundMemberEntity::getStartTime, LinkedHashMap::new, Collectors.toList()));
		return startTimes.map(startTime -> MyRoundRecordResponse.from(roundsByStartTime.get(startTime),
				rowsByStartTime.getOrDefault(startTime, List.of())));
	}

	public RoundDetailResponse roundDetail(String startTime, long memberId) {
		MultigameRoundEntity round = roundRepository.findById(startTime)
				.orElseThrow(() -> new BaseException(ErrorCode.MULTIGAME_RESULT_NOT_FOUND));
		// 전체 처리 시계열(익명)을 조회하며, 내 기록 여부는 mine 플래그로 표시한다.
		List<MultigameRoundLogEntity> timelineLogs = logRepository.findByStartTimeOrderByAttemptedAtAsc(startTime);
		return RoundDetailResponse.from(round, timelineLogs, memberId);
	}
}
