package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MultigameResultRepository extends JpaRepository<MultigameResultEntity, String> {

	@Query(value = """
			SELECT * FROM multigame_result
			WHERE start_time LIKE :datePattern
			ORDER BY start_time ASC
			""", nativeQuery = true)
	List<MultigameResultEntity> findByDate(@Param("datePattern") String datePattern);

	@Query("SELECT COUNT(r) FROM MultigameResultEntity r")
	long countTotalGames();

	@Query("SELECT COALESCE(SUM(r.participantCount), 0) FROM MultigameResultEntity r")
	long countTotalParticipants();

	@Query("SELECT COALESCE(AVG(r.participantCount), 0) FROM MultigameResultEntity r")
	double calculateAverageParticipants();
}
