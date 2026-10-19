package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.singlegame.config.SingleGameProperties;
import com.mjusugangsincheonghelper.singlegame.dto.AnalysisResponse;
import com.mjusugangsincheonghelper.singlegame.dto.DepartmentsResponse;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import com.mjusugangsincheonghelper.singlegame.dto.RankingResponse;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveRequest;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameSaveResponse;
import com.mjusugangsincheonghelper.singlegame.dto.cache.FeedbackQuantileTable;
import com.mjusugangsincheonghelper.singlegame.dto.cache.QuantileBand;
import com.mjusugangsincheonghelper.singlegame.dto.cache.SequenceQuantileTable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SingleGameRecordService 단위 테스트")
class SingleGameRecordServiceTest {

	@Mock
	private SingleGameRepository singleGameRepository;

	private SingleGameRecordService recordService;

	@BeforeEach
	void setUp() {
		recordService = new SingleGameRecordService(singleGameRepository);
	}

	@Nested
	@DisplayName("getMyRecords 메서드는")
	class Describe_getMyRecords {

		@Test
		@DisplayName("회원의 게임 기록 목록을 페이지로 반환한다")
		void it_returns_my_records_page() throws Exception {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(1L).tTotal(5000).tEnterMain(2000)
					.isCompleted(true).totalCourses(6)
					.build();
			Field idField = SingleGameEntity.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(game, 100L);

			given(singleGameRepository.findByMemberIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
					.willReturn(new PageImpl<>(List.of(game)));

			Page<MyRecordResponse> records = recordService.getMyRecords(1L, 0, 10);

			assertThat(records.getContent()).hasSize(1);
			assertThat(records.getContent().get(0).getGameId()).isEqualTo(100L);
			assertThat(records.getContent().get(0).getTotalCourses()).isEqualTo(6);
			assertThat(records.getContent().get(0).getTTotal()).isEqualTo(5000);
		}
	}
}
