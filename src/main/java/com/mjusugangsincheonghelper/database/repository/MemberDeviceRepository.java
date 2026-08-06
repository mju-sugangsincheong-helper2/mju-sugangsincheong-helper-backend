package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	/** 최근 N일 이내 접속한 활성 기기 수 (도메인 지표) */
	long countByLastAccessedAtGreaterThanEqual(java.time.Instant since);

	/** OS별 기기 분포 (도메인 지표) */
	@Query("select d.platformjsOs, count(d) from MemberDevice d where d.platformjsOs is not null and d.platformjsOs <> '' group by d.platformjsOs order by count(d) desc")
	List<Object[]> countByPlatformjsOs();

	/** 브라우저별 기기 분포 (도메인 지표) */
	@Query("select d.platformjsName, count(d) from MemberDevice d where d.platformjsName is not null and d.platformjsName <> '' group by d.platformjsName order by count(d) desc")
	List<Object[]> countByPlatformjsName();

	/** 만료된 기기 세션 일괄 삭제 (만료 FCM 토큰 정리). 삭제된 개수를 반환한다. */
	@Modifying
	@Query("delete from MemberDevice d where d.expiresAt is not null and d.expiresAt < :now")
	int deleteExpired(java.time.Instant now);
}
