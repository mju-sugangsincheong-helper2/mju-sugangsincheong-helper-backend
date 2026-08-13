package com.mjusugangsincheonghelper.auth.session.device;

import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.session.token.RefreshTokenHasher;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSessionService {

	private final MemberDeviceRepository memberDeviceRepository;

	@Transactional
	public MemberDevice upsert(Long memberId, String refreshToken, DeviceInfo deviceInfo, long expiryMs) {
		final DeviceInfo info = deviceInfo != null ? deviceInfo : DeviceInfo.builder().build();
		final String refreshTokenHash = RefreshTokenHasher.hash(refreshToken);

		Optional<MemberDevice> existing = findExistingDevice(memberId, info);

		return existing
				.map(device -> {
					device.updateAccessInfo(
							refreshTokenHash,
							info.getName(),
							info.getVersion(),
							info.getLayout(),
							info.getPrerelease(),
							info.getOs(),
							info.getManufacturer(),
							info.getProduct(),
							info.getDescription(),
							info.getUa()
					);
					if (info.getFirebaseCloudMessagingRegistrationToken() != null) {
						device.updateFirebaseCloudMessagingRegistrationToken(info.getFirebaseCloudMessagingRegistrationToken());
					}
					// 동일 기기 재로그인: 만료 시각도 함께 연장하지 않으면
					// 최초 생성 시점+7일 이후로 디바이스가 영구 만료 상태가 된다.
					device.extendExpiry(Instant.now().plusMillis(expiryMs));
					return device;
				})
				.orElseGet(() -> {
					MemberDevice device = MemberDevice.builder()
							.memberId(memberId)
							.firebaseInstallationId(info.getFirebaseInstallationId())
							.refreshTokenHash(refreshTokenHash)
							.platformJsName(info.getName())
							.platformJsVersion(info.getVersion())
							.platformJsLayout(info.getLayout())
							.platformJsPrerelease(info.getPrerelease())
							.platformJsOs(info.getOs())
							.platformJsManufacturer(info.getManufacturer())
							.platformJsProduct(info.getProduct())
							.platformJsDescription(info.getDescription())
							.platformJsUa(info.getUa())
							.expiresAt(Instant.now().plusMillis(expiryMs))
							.build();
					if (info.getFirebaseCloudMessagingRegistrationToken() != null) {
						device.updateFirebaseCloudMessagingRegistrationToken(info.getFirebaseCloudMessagingRegistrationToken());
					}
					return memberDeviceRepository.save(device);
				});
	}

	private Optional<MemberDevice> findExistingDevice(Long memberId, DeviceInfo deviceInfo) {
		if (deviceInfo.getFirebaseInstallationId() != null && !deviceInfo.getFirebaseInstallationId().isBlank()) {
			return memberDeviceRepository.findByMemberIdAndFirebaseInstallationId(
					memberId, deviceInfo.getFirebaseInstallationId());
		}
		return Optional.empty();
	}

	@Transactional
	public void deleteByRefreshToken(String refreshToken) {
		if (refreshToken != null) {
			memberDeviceRepository.deleteByRefreshTokenHash(RefreshTokenHasher.hash(refreshToken));
		}
	}

	@Transactional(readOnly = true)
	public List<MemberDevice> findByMemberId(Long memberId) {
		return memberDeviceRepository.findByMemberId(memberId);
	}

	@Transactional
	public void switchMember(Long memberId, Long newMemberId) {
		List<MemberDevice> guestDevices = memberDeviceRepository.findByMemberId(memberId);
		List<MemberDevice> targetDevices = memberDeviceRepository.findByMemberId(newMemberId);

		for (MemberDevice guestDevice : guestDevices) {
			boolean duplicateExists = targetDevices.stream().anyMatch(td ->
					(td.getPlatformJsUa() != null && td.getPlatformJsUa().equals(guestDevice.getPlatformJsUa()))
							|| (td.getFirebaseCloudMessagingRegistrationToken() != null && td.getFirebaseCloudMessagingRegistrationToken().equals(guestDevice.getFirebaseCloudMessagingRegistrationToken()))
			);
			if (duplicateExists) {
				memberDeviceRepository.delete(guestDevice);
			} else {
				guestDevice.switchMember(newMemberId);
			}
		}
	}

	/**
	 * 만료된 기기 세션(세션 만료 시각이 지난 기기, Firebase Cloud Messaging 토큰 포함)을 일괄 삭제한다.
	 * 관리자 정리 버튼용: 삭제된 개수를 반환한다.
	 */
	@Transactional
	public long deleteExpired() {
		long deletedCount = memberDeviceRepository.deleteExpired(java.time.Instant.now());
		log.info("Cleaned up expired device sessions. deletedCount={}", deletedCount);
		return deletedCount;
	}

	/**
	 * 만료된 디바이스 세션을 별도 트랜잭션(REQUIRES_NEW)에서 즉시 삭제한다.
	 * <p>{@code SessionService.refreshSession}의 만료 분기에서 호출된다.
	 * 호출 컨텍스트(refresh)가 이어서 예외를 던지며 rollback되더라도,
	 * 이 삭제는 별도 트랜잭션에서 이미 커밋됐으므로 취소되지 않는다.
	 * <p>(이전 구현은 같은 트랜잭션에서 delete 후 throw해 rollback되어
	 * 만료 디바이스가 DB에 잔존하며 계속 재사용되는 버그가 있었다.)
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void purgeDevice(Long deviceId) {
		memberDeviceRepository.findById(deviceId).ifPresent(memberDeviceRepository::delete);
		log.info("Purged expired device session. deviceId={}", deviceId);
	}
}
