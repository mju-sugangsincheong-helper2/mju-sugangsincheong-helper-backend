package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity.ExchangeRoomIntentId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRoomIntentRepository extends JpaRepository<ExchangeRoomIntentEntity, ExchangeRoomIntentId> {

	List<ExchangeRoomIntentEntity> findByTermAndMemberId(String term, Long memberId);

	List<ExchangeRoomIntentEntity> findByTermAndMemberIdAndIsOnTrueAndIsDeletedFalse(String term, Long memberId);

	List<ExchangeRoomIntentEntity> findByTermAndRoomId(String term, Long roomId);

	List<ExchangeRoomIntentEntity> findByTermAndIntentId(String term, Long intentId);

	List<ExchangeRoomIntentEntity> findByTermAndRoomIdAndMemberIdAndIsDeletedFalse(String term, Long roomId, Long memberId);

	List<ExchangeRoomIntentEntity> findByTermAndRoomIdAndMemberId(String term, Long roomId, Long memberId);

	@Query("SELECT DISTINCT ri.memberId FROM ExchangeRoomIntentEntity ri WHERE ri.term = :term AND ri.roomId = :roomId")
	List<Long> findDistinctMemberIdsByTermAndRoomId(@Param("term") String term, @Param("roomId") Long roomId);

	/** 현재 학기 배통된(방에 묶인) 활성 Intent 수 (도메인 지표 - 매칭률 계산용) */
	@Query("SELECT COUNT(DISTINCT ri.intentId) FROM ExchangeRoomIntentEntity ri WHERE ri.term = :term AND ri.isDeleted = false")
	long countDistinctIntentIdByTermAndIsDeletedFalse(@Param("term") String term);
}
