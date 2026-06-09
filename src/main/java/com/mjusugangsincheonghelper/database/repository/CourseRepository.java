package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.CourseEntity;
import com.mjusugangsincheonghelper.database.entity.CourseEntity.CourseId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CourseRepository extends JpaRepository<CourseEntity, CourseId> {
	List<CourseEntity> findByTerm(String term);

	@Transactional
	Long deleteByTerm(String term);
}
