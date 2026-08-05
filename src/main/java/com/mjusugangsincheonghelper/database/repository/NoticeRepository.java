package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.NoticeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<NoticeEntity, Long> {

	List<NoticeEntity> findAllByOrderByCreatedAtDesc();
}
