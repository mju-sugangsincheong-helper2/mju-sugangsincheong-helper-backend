package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
