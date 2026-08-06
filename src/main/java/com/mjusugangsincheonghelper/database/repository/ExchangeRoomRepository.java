package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity.ExchangeRoomId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRoomRepository extends JpaRepository<ExchangeRoomEntity, ExchangeRoomId> {

	/** 현재 학기 활성(ACTIVE) 채팅방 수 (도메인 지표) */
	long countByTermAndStatus(String term, String status);

	/** 현재 학기 채팅방 상태(ACTIVE/PARTIAL_OFF/PARTIAL_DELETE/ALL_DELETE)별 개수 */
	@Query("SELECT r.status, COUNT(r) FROM ExchangeRoomEntity r WHERE r.term = :term GROUP BY r.status")
	List<Object[]> countByTermGroupByStatus(@Param("term") String term);

	Optional<ExchangeRoomEntity> findByTermAndCycleHash(String term, String cycleHash);

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("SELECT r FROM ExchangeRoomEntity r WHERE r.term = :term AND r.id = :id")
	Optional<ExchangeRoomEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("term") String term, @org.springframework.data.repository.query.Param("id") Long id);
}
