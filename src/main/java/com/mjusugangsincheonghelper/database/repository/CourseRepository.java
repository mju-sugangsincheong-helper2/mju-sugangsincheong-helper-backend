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
		  AND (CAST(:deptcd AS string) IS NULL OR c.deptcd = :deptcd)
		  AND (CAST(:campus AS string) IS NULL OR c.campusdiv = :campus)
		  AND (CAST(:likeKeyword AS string) IS NULL OR LOWER(c.curinm) LIKE LOWER(CAST(:likeKeyword AS string)) OR c.curinum LIKE CAST(:likeKeyword AS string) OR LOWER(c.profnm) LIKE LOWER(CAST(:likeKeyword AS string)))
		ORDER BY c.curinm ASC, c.classdiv ASC
	""")
	List<CourseEntity> searchSections(@Param("term") String term, @Param("deptcd") String deptcd, @Param("campus") String campus, @Param("likeKeyword") String likeKeyword);

	@Transactional
	Long deleteByTerm(String term);
}
