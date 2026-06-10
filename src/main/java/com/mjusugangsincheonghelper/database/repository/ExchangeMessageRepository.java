package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeMessageEntity.ExchangeMessageId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeMessageRepository extends JpaRepository<ExchangeMessageEntity, ExchangeMessageId> {

	List<ExchangeMessageEntity> findByTermAndRoomIdAndIdLessThanOrderByIdDesc(String term, Long roomId, Long id, Pageable pageable);

	Optional<ExchangeMessageEntity> findTopByTermAndRoomIdOrderByIdDesc(String term, Long roomId);
}
