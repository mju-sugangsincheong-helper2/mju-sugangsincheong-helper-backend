package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberAuthRepository extends JpaRepository<MemberAuth, Long> {

	Optional<MemberAuth> findByAuthKeyAndAuthType(String authKey, AuthType authType);

	Optional<MemberAuth> findByMemberIdAndAuthType(Long memberId, AuthType authType);

	Optional<MemberAuth> findByMemberId(Long memberId);
}
