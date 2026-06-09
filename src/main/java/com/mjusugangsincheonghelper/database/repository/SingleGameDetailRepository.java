package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.SingleGameDetailEntity;
import com.mjusugangsincheonghelper.database.entity.SingleGameDetailId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SingleGameDetailRepository extends JpaRepository<SingleGameDetailEntity, SingleGameDetailId> {

	List<SingleGameDetailEntity> findByGameIdOrderBySequenceAsc(Long gameId);
}
