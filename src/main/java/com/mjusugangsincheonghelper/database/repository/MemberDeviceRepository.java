package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberDeviceRepository extends JpaRepository<MemberDevice, Long> {

	Optional<MemberDevice> findByRefreshTokenHash(String refreshTokenHash);

	Optional<MemberDevice> findByMemberIdAndFirebaseInstallationId(Long memberId, String firebaseInstallationId);

	List<MemberDevice> findByMemberId(Long memberId);

	List<MemberDevice> findAllByFirebaseCloudMessagingRegistrationToken(String firebaseCloudMessagingRegistrationToken);

	/** 전체 사용자의 등록된 Firebase Cloud Messaging Registration Token 목록 (broadcast 알림용) */
	@Query("select d.firebaseCloudMessagingRegistrationToken from MemberDevice d where d.firebaseCloudMessagingRegistrationToken is not null and d.firebaseCloudMessagingRegistrationToken <> ''")
	List<String> findAllFirebaseCloudMessagingRegistrationTokens();

	void deleteByRefreshTokenHash(String refreshTokenHash);
}
