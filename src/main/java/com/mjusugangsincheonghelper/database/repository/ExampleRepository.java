package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExampleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExampleRepository extends JpaRepository<ExampleEntity, Long> {

	Page<ExampleEntity> findByActiveTrue(Pageable pageable);
}
