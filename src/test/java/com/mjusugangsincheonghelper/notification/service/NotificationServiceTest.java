package com.mjusugangsincheonghelper.notification.service;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenDeleteRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenRegisterRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private MemberDeviceRepository memberDeviceRepository;

	@Mock
	private com.mjusugangsincheonghelper.global.config.PgmqService pgmqService;

	private NotificationService notificationService;

	@BeforeEach
	void setUp() {
		notificationService = new NotificationService(memberDeviceRepository, pgmqService);
	}

	@Test
	@DisplayName("FCM 토큰 등록 시 deviceId로 기기를 특정하여 토큰을 저장한다")
	void shouldRegisterFcmTokenSuccessfully() {
		Long memberId = 1L;
		Long deviceId = 10L;
		String token = "sample-fcm-token-123";
		MemberDevice device = MemberDevice.builder()
				.memberId(memberId)
				.refreshTokenHash("ref-token-1")
				.build();

		given(memberDeviceRepository.findById(deviceId)).willReturn(Optional.of(device));
		given(memberDeviceRepository.findAllByFirebaseCloudMessagingRegistrationToken(token)).willReturn(List.of());

		NotificationTokenRegisterRequest request = NotificationTokenRegisterRequest.builder()
				.firebaseCloudMessagingRegistrationToken(token)
				.build();

		NotificationTokenResponse response = notificationService.registerToken(memberId, deviceId, request);

		assertThat(response.getFirebaseCloudMessagingRegistrationToken()).isEqualTo(token);
		assertThat(device.getFirebaseCloudMessagingRegistrationToken()).isEqualTo(token);
	}

	@Test
	@DisplayName("다른 사용자의 deviceId로 요청 시 예외가 발생한다")
	void shouldThrowWhenDeviceBelongsToOtherMember() {
		Long currentMemberId = 1L;
		Long otherMemberId = 2L;
		Long deviceId = 10L;
		String token = "sample-fcm-token-123";
		MemberDevice device = MemberDevice.builder()
				.memberId(otherMemberId)
				.refreshTokenHash("ref-token-1")
				.build();

		given(memberDeviceRepository.findById(deviceId)).willReturn(Optional.of(device));

		NotificationTokenRegisterRequest request = NotificationTokenRegisterRequest.builder()
				.firebaseCloudMessagingRegistrationToken(token)
				.build();

		assertThatThrownBy(() -> notificationService.registerToken(currentMemberId, deviceId, request))
				.isInstanceOf(BaseException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.GLOBAL_SECURITY_FORBIDDEN);
	}

	@Test
	@DisplayName("FCM 토큰 삭제 시 deviceId로 기기를 특정하여 토큰을 클리어한다")
	void shouldDeleteFcmTokenSuccessfully() {
		Long memberId = 1L;
		Long deviceId = 10L;
		String token = "sample-fcm-token-123";
		MemberDevice device = MemberDevice.builder()
				.memberId(memberId)
				.refreshTokenHash("ref-token-1")
				.build();
		device.updateFirebaseCloudMessagingRegistrationToken(token);

		given(memberDeviceRepository.findById(deviceId)).willReturn(Optional.of(device));

		NotificationTokenDeleteRequest request = NotificationTokenDeleteRequest.builder()
				.firebaseCloudMessagingRegistrationToken(token)
				.build();

		notificationService.deleteToken(memberId, deviceId, request);

		assertThat(device.getFirebaseCloudMessagingRegistrationToken()).isNull();
	}

	@Test
	@DisplayName("다른 사용자의 deviceId로 삭제 요청 시 예외가 발생한다")
	void shouldThrowWhenDeletingOtherMemberDevice() {
		Long currentMemberId = 1L;
		Long otherMemberId = 2L;
		Long deviceId = 10L;
		String token = "sample-fcm-token-123";
		MemberDevice device = MemberDevice.builder()
				.memberId(otherMemberId)
				.refreshTokenHash("ref-token-1")
				.build();
		device.updateFirebaseCloudMessagingRegistrationToken(token);

		given(memberDeviceRepository.findById(deviceId)).willReturn(Optional.of(device));

		NotificationTokenDeleteRequest request = NotificationTokenDeleteRequest.builder()
				.firebaseCloudMessagingRegistrationToken(token)
				.build();

		assertThatThrownBy(() -> notificationService.deleteToken(currentMemberId, deviceId, request))
				.isInstanceOf(BaseException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.GLOBAL_SECURITY_FORBIDDEN);
	}
}
