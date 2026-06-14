package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity.ExchangeIntentId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeIntentRepository extends JpaRepository<ExchangeIntentEntity, ExchangeIntentId> {

	List<ExchangeIntentEntity> findByTermAndMemberIdAndIsDeletedFalseOrderByIdDesc(String term, Long memberId);

	List<ExchangeIntentEntity> findByTermAndIsDeletedFalse(String term);

	List<ExchangeIntentEntity> findByTermAndIsDeletedFalseOrderByIdDesc(String term, Pageable pageable);

	List<ExchangeIntentEntity> findByTermAndIsDeletedFalseAndIdLessThanOrderByIdDesc(String term, Long id, Pageable pageable);

	List<ExchangeIntentEntity> findByTermAndMemberIdAndGiveCourseNoAndWantCourseNoAndIsDeletedFalse(String term, Long memberId, String giveCourseNo, String wantCourseNo);
}
