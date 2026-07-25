package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.Member;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {

	@Query("SELECT m.department, COUNT(m) FROM Member m WHERE m.department IS NOT NULL GROUP BY m.department")
	List<Object[]> countMembersByDepartment();
}
