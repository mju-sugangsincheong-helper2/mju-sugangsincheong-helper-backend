package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity.ExchangeRoomMessageId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRoomMessageRepository extends JpaRepository<ExchangeRoomMessageEntity, ExchangeRoomMessageId> {

	List<ExchangeRoomMessageEntity> findByTermAndRoomIdAndIdLessThanOrderByIdDesc(String term, Long roomId, Long id, Pageable pageable);

	List<ExchangeRoomMessageEntity> findByTermAndRoomIdOrderByIdDesc(String term, Long roomId, Pageable pageable);

	Optional<ExchangeRoomMessageEntity> findTopByTermAndRoomIdOrderByIdDesc(String term, Long roomId);

	int countByTermAndRoomIdAndIdGreaterThan(String term, Long roomId, Long id);
}
