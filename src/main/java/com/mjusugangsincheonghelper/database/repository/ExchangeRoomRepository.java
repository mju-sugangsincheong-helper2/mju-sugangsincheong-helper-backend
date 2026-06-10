package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity.ExchangeRoomId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRoomRepository extends JpaRepository<ExchangeRoomEntity, ExchangeRoomId> {

	Optional<ExchangeRoomEntity> findByTermAndCycleHash(String term, String cycleHash);
}
