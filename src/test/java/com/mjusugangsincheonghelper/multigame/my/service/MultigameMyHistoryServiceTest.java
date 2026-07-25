package com.mjusugangsincheonghelper.multigame.my.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultRepository;
import com.mjusugangsincheonghelper.multigame.my.dto.MyHistoryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameMyHistoryService 테스트")
class MultigameMyHistoryServiceTest {

	@Mock
	private MultigameResultDetailRepository resultDetailRepository;

	@Mock
	private MultigameResultRepository resultRepository;

	@InjectMocks
	private MultigameMyHistoryService myHistoryService;

	@Test
	@DisplayName("내 참여 기록 목록을 조회한다")
	void getMyHistory_returns_paged_history() {
		// given
		Long memberId = 1L;
		Pageable pageable = PageRequest.of(0, 10);

		MultigameResultDetailEntity detail = MultigameResultDetailEntity.builder()
				.startTime("20260630120000")
				.memberId(memberId)
				.subjectId(3)
				.status("SUCCESS")
				.build();

		MultigameResultEntity result = MultigameResultEntity.builder()
				.startTime("20260630120000")
				.participantCount(100)
				.capacity(50)
				.finalizedAt(Instant.now())
				.build();

		Page<MultigameResultDetailEntity> detailPage = new PageImpl<>(List.of(detail), pageable, 1);

		given(resultDetailRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable))
				.willReturn(detailPage);
		given(resultRepository.findById("20260630120000"))
				.willReturn(Optional.of(result));

		// when
		Page<MyHistoryResponse> historyPage = myHistoryService.getMyHistory(memberId, pageable);

		// then
		assertThat(historyPage.getContent()).hasSize(1);
		assertThat(historyPage.getContent().get(0).getMultigameId()).isEqualTo("20260630120000");
		assertThat(historyPage.getContent().get(0).getSubjectId()).isEqualTo(3);
		assertThat(historyPage.getContent().get(0).getStatus()).isEqualTo("SUCCESS");
		assertThat(historyPage.getContent().get(0).getParticipantCount()).isEqualTo(100);
	}
}
