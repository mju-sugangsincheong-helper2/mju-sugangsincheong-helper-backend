package com.mjusugangsincheonghelper.auth.test;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.session.SessionResult;
import com.mjusugangsincheonghelper.auth.session.SessionService;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Test Auth", description = "테스트 인증 API (dev 전용)")
@RestController
@Profile("dev")
@RequiredArgsConstructor
@RequestMapping("/api/{version}/auth")
public class TestAuthController {

	private final MemberAuthRepository memberAuthRepository;
	private final SessionService sessionService;
	private final TestAccountInitializer testAccountInitializer;

	@GetMapping(value = "/test-accounts", version = "1+")
	@Operation(
			summary = "List test accounts",
			description = "사용 가능한 테스트 계정 목록을 반환합니다. (dev 환경 전용)",
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
					+ "사용 가능한 계정 목록은 GET /api/v1/auth/test-accounts 를 호출하여 확인할 수 있습니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "로그인 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.AUTH_MEMBER_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<TestLoginResponse>> testLogin(
			@RequestParam String name,
			HttpServletResponse response) {
		String testKey = "test_" + name;

		MemberAuth memberAuth = memberAuthRepository.findByAuthKeyAndAuthType(testKey, AuthType.TEST)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		AuthenticatedIdentity identity = AuthenticatedIdentity.builder()
				.memberId(memberAuth.getMemberId())
				.build();

		SessionResult session = sessionService.createSession(identity, null, null, response);

		TestLoginResponse testLoginResponse = TestLoginResponse.builder()
				.memberId(session.getMemberId())
				.role(session.getRole())
				.name(session.getName())
				.position(session.getPosition())
				.department(session.getDepartment())
				.accessToken(session.getAccessToken())
				.refreshToken(session.getRefreshToken())
				.build();

		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(testLoginResponse));
	}
}
