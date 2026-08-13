package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	/** 최근 N일 이내 접속한 활성 기기 수 (도메인 지표) */
	long countByLastAccessedAtGreaterThanEqual(java.time.Instant since);

	/** OS별 기기 분포 (도메인 지표) */
	@Query("select d.platformJsOs, count(d) from MemberDevice d where d.platformJsOs is not null and d.platformJsOs <> '' group by d.platformJsOs order by count(d) desc")
	List<Object[]> countByPlatformJsOs();

	/** 브라우저별 기기 분포 (도메인 지표) */
	@Query("select d.platformJsName, count(d) from MemberDevice d where d.platformJsName is not null and d.platformJsName <> '' group by d.platformJsName order by count(d) desc")
	List<Object[]> countByPlatformJsName();

	/** 만료된 기기 세션 일괄 삭제 (만료 Firebase Cloud Messaging 토큰 정리). 삭제된 개수를 반환한다. */
	@Modifying
	@Query("delete from MemberDevice d where d.expiresAt is not null and d.expiresAt < :now")
	int deleteExpired(java.time.Instant now);
}
