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
import java.util.List;
import java.util.Optional;
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

	public Page<MyRoundRecordResponse> myRecords(long memberId, int page, int size) {
		return memberRepository.findByMemberIdOrderByStartTimeDesc(memberId, PageRequest.of(page, size))
				.map(MyRoundRecordResponse::from);
	}

	public RoundDetailResponse roundDetail(String startTime, long memberId) {
		MultigameRoundEntity round = roundRepository.findById(startTime)
				.orElseThrow(() -> new BaseException(ErrorCode.MULTIGAME_RESULT_NOT_FOUND));
		Optional<MultigameRoundMemberEntity> myRecord = memberRepository.findByStartTimeAndMemberId(startTime, memberId);
		List<MultigameRoundLogEntity> myLogs = myRecord.isPresent()
				? logRepository.findByStartTimeAndMemberIdOrderByAttemptedAtAsc(startTime, memberId)
				: List.of();
		return RoundDetailResponse.from(round, memberRepository.aggregateBySubject(startTime),
				myRecord.orElse(null), myLogs);
	}
}
