package com.mjusugangsincheonghelper.multigame.result.service;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundLogEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundMemberEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundLogRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundMemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.result.dto.MyRoundResult;
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
	 * 페이지네이션 단위는 라운드이며, 한 라운드의 과목별 최종 결과는 results 배열로 묶어 반환한다.
	 */
	public Page<MyRoundRecordResponse> myRecords(long memberId, int page, int size) {
		Page<String> startTimes = memberRepository.findDistinctStartTimesByMemberId(memberId, PageRequest.of(page, size));
		if (startTimes.isEmpty()) {
			return Page.empty(startTimes.getPageable());
		}
		Map<String, List<MultigameRoundMemberEntity>> rowsByStartTime = memberRepository
				.findByStartTimeInAndMemberIdOrderByStartTimeDescSubjectIdAsc(startTimes.getContent(), memberId)
				.stream()
				.collect(Collectors.groupingBy(MultigameRoundMemberEntity::getStartTime, LinkedHashMap::new, Collectors.toList()));
		return startTimes.map(startTime -> MyRoundRecordResponse.from(startTime,
				rowsByStartTime.getOrDefault(startTime, List.of()).stream().map(MyRoundResult::from).toList()));
	}

	public RoundDetailResponse roundDetail(String startTime, long memberId) {
		MultigameRoundEntity round = roundRepository.findById(startTime)
				.orElseThrow(() -> new BaseException(ErrorCode.MULTIGAME_RESULT_NOT_FOUND));
		List<MultigameRoundMemberEntity> myRecords = memberRepository.findByStartTimeAndMemberIdOrderBySubjectIdAsc(startTime, memberId);
		List<MultigameRoundLogEntity> myLogs = !myRecords.isEmpty()
				? logRepository.findByStartTimeAndMemberIdOrderByAttemptedAtAsc(startTime, memberId)
				: List.of();
		return RoundDetailResponse.from(round, memberRepository.aggregateBySubject(startTime), myRecords, myLogs);
	}
}
