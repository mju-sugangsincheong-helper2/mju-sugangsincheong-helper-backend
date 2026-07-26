package com.mjusugangsincheonghelper.multigame.my.service;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.my.dto.MyHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MultigameMyHistoryService {

	private final MultigameResultDetailRepository resultDetailRepository;
	private final MultigameResultRepository resultRepository;

	public Page<MyHistoryResponse> getMyHistory(Long memberId, Pageable pageable) {
		Page<MultigameResultDetailEntity> detailPage = resultDetailRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);

		return detailPage.map(detail -> {
			MultigameResultEntity result = resultRepository.findById(detail.getStartTime())
					.orElseThrow(() -> new BaseException(ErrorCode.MULTIGAME_RESULT_NOT_FOUND));
			return MyHistoryResponse.of(detail, result);
		});
	}
}
