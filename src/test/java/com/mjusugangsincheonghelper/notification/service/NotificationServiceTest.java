package com.mjusugangsincheonghelper.notification.service;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private MemberDeviceRepository memberDeviceRepository;

	private NotificationService notificationService;

	@BeforeEach
	void setUp() {
		notificationService = new NotificationService(memberDeviceRepository);
	}

	@Test
	@DisplayName("FCM 토큰 등록 시 MemberDevice에 새 토큰을 갱신한다")
	void shouldRegisterFcmTokenSuccessfully() {
		Long memberId = 1L;
		String token = "sample-fcm-token-123";
		MemberDevice device = MemberDevice.builder()
				.memberId(memberId)
				.refreshToken("ref-token-1")
				.build();

		given(memberDeviceRepository.findByFcmToken(token)).willReturn(Optional.empty());
		given(memberDeviceRepository.findFirstByMemberIdOrderByLastAccessedAtDesc(memberId)).willReturn(Optional.of(device));

		NotificationTokenRegisterRequest request = NotificationTokenRegisterRequest.builder()
				.fcmToken(token)
				.build();

		NotificationTokenResponse response = notificationService.registerToken(memberId, request);

		assertThat(response.getFcmToken()).isEqualTo(token);
		assertThat(device.getFcmToken()).isEqualTo(token);
	}

	@Test
	@DisplayName("FCM 토큰 삭제 시 해당 기기의 FCM 토큰을 초기화한다")
	void shouldDeleteFcmTokenSuccessfully() {
		Long memberId = 1L;
		String token = "sample-fcm-token-123";
		MemberDevice device = MemberDevice.builder()
				.memberId(memberId)
				.refreshToken("ref-token-1")
				.build();
		device.updateFcmToken(token);

		given(memberDeviceRepository.findByMemberIdAndFcmToken(memberId, token)).willReturn(List.of(device));

		NotificationTokenDeleteRequest request = NotificationTokenDeleteRequest.builder()
				.fcmToken(token)
				.build();

		notificationService.deleteToken(memberId, request);

		assertThat(device.getFcmToken()).isNull();
	}
}
