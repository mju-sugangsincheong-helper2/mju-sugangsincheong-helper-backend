package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MultigameResultDetailRepository extends JpaRepository<MultigameResultDetailEntity, Long> {

	List<MultigameResultDetailEntity> findByStartTime(String startTime);

	Optional<MultigameResultDetailEntity> findByStartTimeAndMemberId(String startTime, Long memberId);
}
