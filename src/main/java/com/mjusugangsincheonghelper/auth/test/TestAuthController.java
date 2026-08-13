package com.mjusugangsincheonghelper.auth.test;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.session.SessionResult;
import com.mjusugangsincheonghelper.auth.session.SessionService;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAgreement;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAgreementRepository;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Test Auth", description = "테스트 인증 API (dev 전용)")
@RestController
@Profile("dev")
@RequiredArgsConstructor
@RequestMapping("/api/{version}/auth")
public class TestAuthController {

	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final MemberAgreementRepository memberAgreementRepository;
	private final SessionService sessionService;
	private final TestAccountInitializer testAccountInitializer;

	@GetMapping(value = "/test-accounts", version = "1+")
	@Operation(
			summary = "List test accounts",
			description = "사용 가능한 테스트 계정 목록을 반환합니다. (dev 환경 전용)\n\n"
					+ "주의: 이 목록은 application-dev.yml 의 app.auth.test-accounts 설정값 기준이며, DB에 저장된 테스트 계정 전체가 아닙니다.\n"
					+ "POST /api/v1/auth/test-accounts 로 생성한 계정은 이 목록에 표시되지 않습니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	public ResponseEntity<SingleSuccessResponseEnvelope<List<TestAccountResponse>>> listTestAccounts() {
		List<TestAccountResponse> accounts = testAccountInitializer.getTestAccounts().stream()
				.map(account -> {
					String[] parts = account.getName().split("/");
					String name = parts.length > 0 ? parts[0].trim() : account.getName();
					return TestAccountResponse.builder()
							.name(name)
							.role(account.getRole().name())
							.build();
				})
				.toList();

		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(accounts));
	}

	@PostMapping(value = "/test-login", version = "1+")
	@Operation(
			summary = "Test login",
			description = "테스트 계정으로 로그인합니다. (dev 환경 전용)\n\n"
					+ "사전 설정된 계정 목록은 GET /api/v1/auth/test-accounts 를 호출하여 확인할 수 있고,\n"
					+ "POST /api/v1/auth/test-accounts 로 생성한 계정은 생성 응답에서 받은 name 으로 로그인할 수 있습니다.\n"
					+ "이름에 해당하는 테스트 계정이 DB에 없으면 해당 이름으로 자동 생성 후 로그인합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "로그인 성공"
					)
			}
	)
	@Transactional
	public ResponseEntity<SingleSuccessResponseEnvelope<TestLoginResponse>> testLogin(
			@RequestParam("name") String name,
			@RequestBody(required = false) DeviceInfo device,
			HttpServletResponse response) {
		String testKey = "test_" + name;

		MemberAuth memberAuth = memberAuthRepository.findByAuthKeyAndAuthType(testKey, AuthType.TEST)
				.orElseGet(() -> {
					Member member = memberRepository.save(Member.builder()
							.role(Role.MEMBER)
							.name(name)
							.position("test")
							.department("test")
							.build());
					memberAgreementRepository.save(MemberAgreement.agree(member.getId()));
					return memberAuthRepository.save(MemberAuth.builder()
							.memberId(member.getId())
							.authType(AuthType.TEST)
							.authKey(testKey)
							.build());
				});

		AuthenticatedIdentity identity = AuthenticatedIdentity.builder()
				.memberId(memberAuth.getMemberId())
				.build();

		SessionResult session = sessionService.createSession(identity, device, response);

		TestLoginResponse testLoginResponse = TestLoginResponse.builder()
				.memberId(session.getMemberId())
				.role(session.getRole())
				.name(session.getName())
				.position(session.getPosition())
				.department(session.getDepartment())
				.sessionAccessToken(session.getSessionAccessToken())
				.sessionRefreshToken(session.getSessionRefreshToken())
				.build();

		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(testLoginResponse));
	}

	@PostMapping(value = "/test-accounts", version = "1+")
	@Operation(
			summary = "Create test account",
			description = "특정 역할을 가진 테스트 계정을 생성하고 즉시 로그인 세션을 반환합니다. (dev 환경 전용)\n\n"
					+ "생성된 계정은 DB에 저장되지만 GET /api/v1/auth/test-accounts 목록에는 표시되지 않습니다.\n"
					+ "응답의 name 으로 POST /api/v1/auth/test-login 을 호출하면 동일 계정으로 재로그인할 수 있습니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "생성 및 로그인 성공"
					)
			}
	)
	@Transactional
	public ResponseEntity<SingleSuccessResponseEnvelope<TestLoginResponse>> createTestAccount(
			@RequestBody(required = false) CreateTestAccountRequest request,
			HttpServletResponse response) {

		Role role = (request != null && request.getRole() != null) ? request.getRole() : Role.MEMBER;

		// 중복 방지를 위한 랜덤 이름 생성
		String randomSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
		String name = "TEST_" + randomSuffix;
		String testKey = "test_" + name;

		Member member = memberRepository.save(Member.builder()
				.role(role)
				.name(name)
				.position("TEST_POSITION")
				.department("TEST_DEPARTMENT")
				.build());

		memberAgreementRepository.save(MemberAgreement.agree(member.getId()));

		memberAuthRepository.save(MemberAuth.builder()
				.memberId(member.getId())
				.authType(AuthType.TEST)
				.authKey(testKey)
				.build());

		AuthenticatedIdentity identity = AuthenticatedIdentity.builder()
				.memberId(member.getId())
				.build();

		SessionResult session = sessionService.createSession(identity,
				request != null ? request.getDevice() : null, response);

		TestLoginResponse testLoginResponse = TestLoginResponse.builder()
				.memberId(session.getMemberId())
				.role(session.getRole())
				.name(session.getName())
				.position(session.getPosition())
				.department(session.getDepartment())
				.sessionAccessToken(session.getSessionAccessToken())
				.sessionRefreshToken(session.getSessionRefreshToken())
				.build();

		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(testLoginResponse));
	}
}
