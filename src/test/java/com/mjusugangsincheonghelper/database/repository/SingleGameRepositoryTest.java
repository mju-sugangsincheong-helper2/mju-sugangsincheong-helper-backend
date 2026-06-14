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
		@DisplayName("완료된 게임을 t_total 오름차순으로 반환한다")
		void it_returns_completed_games_ordered_by_t_total() {
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(3000).tEnterMain(150)
					.isCompleted(true).totalCourses(6).build());
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(4000).tEnterMain(100)
					.isCompleted(false).totalCourses(6).build());

			List<Object[]> rankings = singleGameRepository.findRankingRaw(6);

			assertThat(rankings).hasSize(2);
			assertThat(((Number) rankings.get(0)[5]).intValue()).isEqualTo(3000);
			assertThat(((Number) rankings.get(1)[5]).intValue()).isEqualTo(5000);
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

			List<Object[]> rankings = singleGameRepository.findRankingRaw(6);

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

			List<Object[]> rankings = singleGameRepository.findDeptRankingRaw(6, "컴퓨터공학과");

			assertThat(rankings).hasSize(1);
			assertThat(((Number) rankings.get(0)[5]).intValue()).isEqualTo(5000);
		}
	}

	@Nested
	@DisplayName("countByTotalCoursesAndIsCompletedTrue 메서드는")
	class Describe_countByTotalCoursesAndIsCompletedTrue {

		@Test
		@DisplayName("완료된 게임 수를 반환한다")
		void it_counts_completed_games() {
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(3000).tEnterMain(150)
					.isCompleted(true).totalCourses(6).build());
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(4000).tEnterMain(100)
					.isCompleted(false).totalCourses(6).build());

			long count = singleGameRepository.countByTotalCoursesAndIsCompletedTrue(6);

			assertThat(count).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("findFirstClickRaw 메서드는")
	class Describe_findFirstClickRaw {

		@Test
		@DisplayName("첫 번째 과목의 클릭 속도를 반환한다")
		void it_returns_first_click_data() {
			SingleGameEntity game = singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());

			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(game.getId()).sequence(1)
					.tClickCourse(450).tClickYes(180).tClickOk(200).build());
			singleGameDetailRepository.save(SingleGameDetailEntity.builder()
					.gameId(game.getId()).sequence(2)
					.tClickCourse(300).tClickYes(150).tClickOk(180).build());

			List<Object[]> firstClicks = singleGameRepository.findFirstClickRaw(6);

			assertThat(firstClicks).hasSize(1);
			assertThat(((Number) firstClicks.get(0)[2]).intValue()).isEqualTo(450);
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
