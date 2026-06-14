package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity.ExchangeRoomId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRoomRepository extends JpaRepository<ExchangeRoomEntity, ExchangeRoomId> {

	Optional<ExchangeRoomEntity> findByTermAndCycleHash(String term, String cycleHash);

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("SELECT r FROM ExchangeRoomEntity r WHERE r.term = :term AND r.id = :id")
	Optional<ExchangeRoomEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("term") String term, @org.springframework.data.repository.query.Param("id") Long id);
}
