package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberDeviceRepository extends JpaRepository<MemberDevice, Long> {

	Optional<MemberDevice> findByRefreshTokenHash(String refreshTokenHash);

	Optional<MemberDevice> findByMemberIdAndPlatformjsUaAndFcmToken(Long memberId, String platformjsUa, String fcmToken);

	Optional<MemberDevice> findByMemberIdAndPlatformjsUa(Long memberId, String platformjsUa);

	List<MemberDevice> findByMemberId(Long memberId);

	Optional<MemberDevice> findByFcmToken(String fcmToken);

	Optional<MemberDevice> findFirstByMemberIdOrderByLastAccessedAtDesc(Long memberId);

	/** 전체 사용자의 등록된 FCM 토큰 목록 (broadcast 알림용) */
	@Query("select d.fcmToken from MemberDevice d where d.fcmToken is not null and d.fcmToken <> ''")
	List<String> findAllFcmTokens();

	List<MemberDevice> findByMemberIdAndFcmToken(Long memberId, String fcmToken);

	void deleteByRefreshTokenHash(String refreshTokenHash);
}
