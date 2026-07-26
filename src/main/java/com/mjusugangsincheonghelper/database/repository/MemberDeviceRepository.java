package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberDeviceRepository extends JpaRepository<MemberDevice, Long> {

	Optional<MemberDevice> findByRefreshToken(String refreshToken);

	List<MemberDevice> findByMemberId(Long memberId);

	void deleteByRefreshToken(String refreshToken);
}
