package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.singlegame.dto.MyRecordResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 싱글게임 내 기록 조회 전담. 쓰기 없이 페이징 조회만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SingleGameRecordService {

	private final SingleGameRepository singleGameRepository;

	public Page<MyRecordResponse> getMyRecords(Long memberId, int page, int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<SingleGameEntity> gamesPage = singleGameRepository
				.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
		List<SingleGameEntity> games = gamesPage.getContent();

		List<MyRecordResponse> records = games.stream().map(this::toMyRecord).toList();

		return new PageImpl<>(records, pageable, gamesPage.getTotalElements());
	}

	private MyRecordResponse toMyRecord(SingleGameEntity game) {
		return MyRecordResponse.builder()
				.gameId(game.getId())
				.totalCourses(game.getTotalCourses())
				.completed(game.isCompleted())
				.tTotal(game.getTTotal())
				.createdAt(game.getCreatedAt())
				.build();
	}
}
