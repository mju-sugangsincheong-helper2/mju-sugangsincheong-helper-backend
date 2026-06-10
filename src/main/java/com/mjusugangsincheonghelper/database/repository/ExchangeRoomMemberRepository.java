package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMemberEntity.ExchangeRoomMemberId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRoomMemberRepository extends JpaRepository<ExchangeRoomMemberEntity, ExchangeRoomMemberId> {

	List<ExchangeRoomMemberEntity> findByTermAndMemberId(String term, Long memberId);

	List<ExchangeRoomMemberEntity> findByTermAndRoomId(String term, Long roomId);
}
