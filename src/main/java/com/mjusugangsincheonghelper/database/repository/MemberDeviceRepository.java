package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberDeviceRepository extends JpaRepository<MemberDevice, Long> {

	Optional<MemberDevice> findByRefreshToken(String refreshToken);

	List<MemberDevice> findByMemberId(Long memberId);

	Optional<MemberDevice> findByFcmToken(String fcmToken);

	Optional<MemberDevice> findFirstByMemberIdOrderByLastAccessedAtDesc(Long memberId);

	List<MemberDevice> findByMemberIdAndFcmToken(Long memberId, String fcmToken);

	void deleteByRefreshToken(String refreshToken);
}
