package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MultigameRoundRepository extends JpaRepository<MultigameRoundEntity, String> {

	Page<MultigameRoundEntity> findAllByOrderByStartTimeDesc(Pageable pageable);
}
