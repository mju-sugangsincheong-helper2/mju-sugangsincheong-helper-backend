package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.CourseEntity;
import com.mjusugangsincheonghelper.database.entity.CourseEntity.CourseId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CourseRepository extends JpaRepository<CourseEntity, CourseId> {
	List<CourseEntity> findByTerm(String term);

	boolean existsByTerm(String term);

	@Query("""
		SELECT DISTINCT c.deptcd, c.deptnm, c.campusdiv FROM CourseEntity c
		WHERE c.term = :term AND c.deptcd IS NOT NULL
	""")
	List<Object[]> findDistinctDepartmentsByTerm(@Param("term") String term);

	@Query("""
		SELECT c FROM CourseEntity c
		WHERE c.term = :term
		  AND (:deptcd IS NULL OR c.deptcd = :deptcd)
		  AND (:campus IS NULL OR c.campusdiv = :campus)
		  AND (:likeKeyword IS NULL OR LOWER(c.curinm) LIKE LOWER(:likeKeyword) OR c.curinum LIKE :likeKeyword OR LOWER(c.profnm) LIKE LOWER(:likeKeyword))
		ORDER BY c.curinm ASC, c.classdiv ASC
	""")
	List<CourseEntity> searchSections(@Param("term") String term, @Param("deptcd") String deptcd, @Param("campus") String campus, @Param("likeKeyword") String likeKeyword);

	@Transactional
	Long deleteByTerm(String term);

	/** 학기별 강좌 수 (도메인 지표) */
	@Query("""
		SELECT c.term, count(c) FROM CourseEntity c
		GROUP BY c.term ORDER BY c.term DESC
	""")
	List<Object[]> countByTerm();

	/** 등록된 강좌가 있는 학기 수 (도메인 지표) */
	@Query("SELECT count(DISTINCT c.term) FROM CourseEntity c")
	long countDistinctTerms();
}
