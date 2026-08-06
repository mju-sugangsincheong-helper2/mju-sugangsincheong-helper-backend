package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.Member;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {

	/** 역할별 회원 수 (도메인 지표: 정회원/게스트/관리자 비율) */
	@Query("select m.role, count(m) from Member m group by m.role")
	List<Object[]> countByRole();

	long countByCreatedAtGreaterThanEqual(Instant since);
}
