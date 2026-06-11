package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity.ExchangeRoomMemberId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRoomMemberRepository extends JpaRepository<ExchangeRoomMemberEntity, ExchangeRoomMemberId> {

	List<ExchangeRoomMemberEntity> findByTermAndMemberId(String term, Long memberId);

	List<ExchangeRoomMemberEntity> findByTermAndRoomId(String term, Long roomId);

	@Query("SELECT DISTINCT rm.memberId FROM ExchangeRoomMemberEntity rm WHERE rm.term = :term AND rm.roomId = :roomId")
	List<Long> findMemberIdsByRoomId(@Param("term") String term, @Param("roomId") Long roomId);
}
