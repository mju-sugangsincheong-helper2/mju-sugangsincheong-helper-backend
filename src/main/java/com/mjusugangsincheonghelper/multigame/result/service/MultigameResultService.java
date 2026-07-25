package com.mjusugangsincheonghelper.multigame.result.service;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameResultDetailResponse;
import com.mjusugangsincheonghelper.multigame.result.dto.MultigameResultResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MultigameResultService {

	private final MultigameResultRepository resultRepository;
	private final MultigameResultDetailRepository resultDetailRepository;

	public MultigameResultResponse getGameResult(String multigameId) {
		MultigameResultEntity result = resultRepository.findById(multigameId)
				.orElseThrow(() -> new BaseException(ErrorCode.MULTIGAME_RESULT_NOT_FOUND));

		List<MultigameResultDetailResponse> details = resultDetailRepository.findByStartTime(multigameId)
				.stream()
				.map(MultigameResultDetailResponse::from)
				.toList();

		return MultigameResultResponse.of(result, details);
	}

	public MultigameResultDetailResponse getMyResult(String multigameId, Long memberId) {
		MultigameResultDetailEntity detail = resultDetailRepository.findByStartTimeAndMemberId(multigameId, memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.MULTIGAME_RESULT_NOT_FOUND));

		return MultigameResultDetailResponse.from(detail);
	}
}
