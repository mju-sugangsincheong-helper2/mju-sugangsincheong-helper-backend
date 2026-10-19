package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("SingleGameRepository 테스트")
class SingleGameRepositoryTest {

	@Autowired
	private SingleGameRepository singleGameRepository;

	@Autowired
	private SingleGameDetailRepository singleGameDetailRepository;

	@Autowired
	private MemberRepository memberRepository;

	private Member testMember;

	@BeforeEach
	void setUp() {
		singleGameDetailRepository.deleteAll();
		singleGameRepository.deleteAll();
		memberRepository.deleteAll();

		testMember = memberRepository.save(Member.builder()
				.role(Member.Role.MEMBER)
				.name("테스트유저")
				.department("컴퓨터공학과")
				.build());
	}

	@Nested
	@DisplayName("save 메서드는")
	class Describe_save {

		@Test
		@DisplayName("게임 엔티티를 저장하고 ID를 부여한다")
		void it_saves_game_and_assigns_id() {
			SingleGameEntity game = SingleGameEntity.builder()
					.memberId(testMember.getId())
					.tTotal(5000)
					.tEnterMain(200)
					.isCompleted(true)
					.totalCourses(6)
					.build();

			SingleGameEntity saved = singleGameRepository.save(game);

			assertThat(saved.getId()).isNotNull();
			assertThat(saved.getMemberId()).isEqualTo(testMember.getId());
		}
	}

	@Nested
	@DisplayName("findRankingRaw 메서드는")
	class Describe_findRankingRaw {

		@Test
		@DisplayName("완료된 게임 중 사용자별 최고 기록만 t_total 오름차순으로 반환한다")
		void it_returns_completed_games_ordered_by_t_total() {
			Member otherMember = memberRepository.save(Member.builder()
					.role(Member.Role.MEMBER)
					.name("다른유저")
					.department("컴퓨터공학과")
					.build());

			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(3000).tEnterMain(150)
					.isCompleted(true).totalCourses(6).build());
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(otherMember.getId()).tTotal(4000).tEnterMain(100)
					.isCompleted(true).totalCourses(6).build());

			List<Object[]> rankings = singleGameRepository.findRankingRaw(6, 20);

			assertThat(rankings).hasSize(2);
			assertThat(((Number) rankings.get(0)[5]).intValue()).isEqualTo(3000);
			assertThat(((Number) rankings.get(1)[5]).intValue()).isEqualTo(4000);
		}

		@Test
		@DisplayName("해당 totalCourses의 완료된 게임만 반환한다")
		void it_filters_by_total_courses() {
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(3000).tEnterMain(150)
					.isCompleted(true).totalCourses(3).build());

			List<Object[]> rankings = singleGameRepository.findRankingRaw(6, 20);

			assertThat(rankings).hasSize(1);
		}
	}

	@Nested
	@DisplayName("findDeptRankingRaw 메서드는")
	class Describe_findDeptRankingRaw {

		@Test
		@DisplayName("특정 학과 유저의 완료된 게임만 반환한다")
		void it_returns_games_by_department() {
			Member otherDeptMember = memberRepository.save(Member.builder()
					.role(Member.Role.MEMBER)
					.name("다른학과유저")
					.department("경영학과")
					.build());

			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(otherDeptMember.getId()).tTotal(3000).tEnterMain(150)
					.isCompleted(true).totalCourses(6).build());

			List<Object[]> rankings = singleGameRepository.findDeptRankingRaw(6, "컴퓨터공학과", 20);

			assertThat(rankings).hasSize(1);
			assertThat(((Number) rankings.get(0)[5]).intValue()).isEqualTo(5000);
		}
	}

	@Nested
	@DisplayName("findGlobalSegmentQuantiles 메서드는")
	class Describe_findGlobalSegmentQuantiles {

		@Test
		@DisplayName("구간별 p-table을 raw 쿼리로 반환한다")
		void it_returns_sequence_bands() {
			SingleGameEntity game = singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(900).tEnterMain(100)
					.isCompleted(true).totalCourses(6).build());
			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(game.getId()).sequence(1)
					.tClickCourse(100).tClickYes(50).tClickOk(50).build());
			SingleGameEntity other = singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(2700).tEnterMain(300)
					.isCompleted(true).totalCourses(6).build());
			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(other.getId()).sequence(1)
					.tClickCourse(300).tClickYes(150).tClickOk(150).build());

			List<Object[]> rows = singleGameRepository.findGlobalSegmentQuantiles(6);

			assertThat(rows).hasSize(1);
			assertThat(((Number) rows.get(0)[0]).intValue()).isEqualTo(1);
			assertThat(rows.get(0)).hasSize(29);
			// cc {100, 300}: q05=110, q10=120, q30=160, q50=200, q70=240, q90=280, q95=290
			assertThat(((Number) rows.get(0)[1]).doubleValue()).isCloseTo(110.0,
					org.assertj.core.api.Assertions.within(0.001));
			assertThat(((Number) rows.get(0)[2]).doubleValue()).isCloseTo(120.0,
					org.assertj.core.api.Assertions.within(0.001));
			assertThat(((Number) rows.get(0)[4]).doubleValue()).isCloseTo(200.0,
					org.assertj.core.api.Assertions.within(0.001));
		}
	}

	@Nested
	@DisplayName("findDepartmentSegmentQuantiles 메서드는")
	class Describe_findDepartmentSegmentQuantiles {

		@Test
		@DisplayName("해당 학과 판만으로 구간표를 만든다")
		void it_returns_dept_sequence_bands() {
			Member otherDept = memberRepository.save(Member.builder()
					.role(Member.Role.MEMBER)
					.name("타과생")
					.department("물리학과")
					.build());
			SingleGameEntity mine = singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(900).tEnterMain(100)
					.isCompleted(true).totalCourses(6).build());
			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(mine.getId()).sequence(1)
					.tClickCourse(100).tClickYes(50).tClickOk(50).build());
			SingleGameEntity theirs = singleGameRepository.save(SingleGameEntity.builder()
					.memberId(otherDept.getId()).tTotal(9000).tEnterMain(1000)
					.isCompleted(true).totalCourses(6).build());
			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(theirs.getId()).sequence(1)
					.tClickCourse(1000).tClickYes(500).tClickOk(500).build());

			List<Object[]> rows = singleGameRepository.findDepartmentSegmentQuantiles(6, "컴퓨터공학과");

			assertThat(rows).hasSize(1);
			// 학과 판 1개뿐이므로 q05==q95==100
			assertThat(((Number) rows.get(0)[1]).doubleValue()).isCloseTo(100.0,
					org.assertj.core.api.Assertions.within(0.001));
			assertThat(((Number) rows.get(0)[7]).doubleValue()).isCloseTo(100.0,
					org.assertj.core.api.Assertions.within(0.001));
		}
	}

	@Nested
	@DisplayName("findFeedbackQuantiles 메서드는")
	class Describe_findFeedbackQuantiles {

		@Test
		@DisplayName("판별 축약값 분위수를 1행으로 반환한다")
		void it_returns_feedback_bands_in_single_row() {
			SingleGameEntity a = singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(400).tEnterMain(100)
					.isCompleted(true).totalCourses(6).build());
			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(a.getId()).sequence(1)
					.tClickCourse(100).tClickYes(50).tClickOk(50).build());
			SingleGameEntity b = singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(1000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());
			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(b.getId()).sequence(1)
					.tClickCourse(300).tClickYes(100).tClickOk(100).build());

			List<Object[]> rows = singleGameRepository.findFeedbackQuantiles(6);

			assertThat(rows).hasSize(1);
			Object[] row = rows.get(0);
			assertThat(row).hasSize(29);
			assertThat(((Number) row[0]).longValue()).isEqualTo(2);
			// avg_cc {100, 300}: q05=110, q10=120, q50=200
			assertThat(((Number) row[1]).doubleValue()).isCloseTo(110.0,
					org.assertj.core.api.Assertions.within(0.001));
			assertThat(((Number) row[2]).doubleValue()).isCloseTo(120.0,
					org.assertj.core.api.Assertions.within(0.001));
			assertThat(((Number) row[4]).doubleValue()).isCloseTo(200.0,
					org.assertj.core.api.Assertions.within(0.001));
			// N<3판만 있어 pace 분포는 비어 있다
			assertThat(row[22]).isNull();
		}
	}

	@Nested
	@DisplayName("findByGameIdOrderBySequenceAsc 메서드는")
	class Describe_findByGameIdOrderBySequenceAsc {

		@Test
		@DisplayName("게임의 상세 기록을 sequence 순으로 반환한다")
		void it_returns_details_ordered_by_sequence() {
			SingleGameEntity game = singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());

			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(game.getId()).sequence(2)
					.tClickCourse(300).tClickYes(150).tClickOk(180).build());
			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(game.getId()).sequence(1)
					.tClickCourse(450).tClickYes(180).tClickOk(200).build());

			List<SingleGameDetailEntity> details = singleGameDetailRepository.findByGameIdOrderBySequenceAsc(game.getId());

			assertThat(details).hasSize(2);
			assertThat(details.get(0).getSequence()).isEqualTo(1);
			assertThat(details.get(1).getSequence()).isEqualTo(2);
		}
	}
}
