package com.mjusugangsincheonghelper.auth.test;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.auth")
public class TestAccountInitializer {

	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;

	private List<TestAccount> testAccounts;

	public List<TestAccount> getTestAccounts() {
		return testAccounts != null ? testAccounts : List.of();
	}

	@PostConstruct
	@Transactional
	public void init() {
		if (testAccounts == null || testAccounts.isEmpty()) {
			return;
		}

		for (TestAccount account : testAccounts) {
			String[] parts = account.getName().split("/");
			if (parts.length != 3) {
				log.warn("Invalid test account format: {}", account.getName());
				continue;
			}

			String name = parts[0].trim();
			String position = parts[1].trim();
			String department = parts[2].trim();
			String testKey = "test_" + name;

			if (memberAuthRepository.findByAuthKeyAndAuthType(testKey, AuthType.TEST).isPresent()) {
				continue;
			}

			Member member = Member.builder()
					.role(account.getRole())
					.name(name)
					.position(position)
					.department(department)
					.build();
			member = memberRepository.save(member);

			MemberAuth memberAuth = MemberAuth.builder()
					.memberId(member.getId())
					.authType(AuthType.TEST)
					.authKey(testKey)
					.build();
			memberAuthRepository.save(memberAuth);

			log.info("Created test account: {} ({})", name, account.getRole());
		}
	}

	public void setTestAccounts(List<TestAccount> testAccounts) {
		this.testAccounts = testAccounts;
	}

	public static class TestAccount {
		private String name;
		private Role role;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Role getRole() {
			return role;
		}

		public void setRole(Role role) {
			this.role = role;
		}
	}
}
