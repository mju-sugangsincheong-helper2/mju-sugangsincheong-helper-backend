package com.mjusugangsincheonghelper.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GlobalFirebaseConfig {

	@Value("${app.firebase.admin.project-id:}")
	private String projectId;

	@Value("${app.firebase.admin.client-email:}")
	private String clientEmail;

	@Value("${app.firebase.admin.private-key:}")
	private String privateKey;

	@Value("${app.firebase.admin.type:service_account}")
	private String type;

	@Value("${app.firebase.admin.private-key-id:}")
	private String privateKeyId;

	@Value("${app.firebase.admin.client-id:}")
	private String clientId;

	@PostConstruct
	public void init() {
		if (projectId.isBlank() || clientEmail.isBlank() || privateKey.isBlank()) {
			log.warn("Firebase Admin credentials are not set. FirebaseApp will not be initialized.");
			return;
		}

		try {
			if (!FirebaseApp.getApps().isEmpty()) {
				log.info("FirebaseApp is already initialized.");
				return;
			}

			String formattedPrivateKey = privateKey.replace("\\n", "\n");
			String credentialsJson = String.format("""
					{
					  "type": "%s",
					  "project_id": "%s",
					  "private_key_id": "%s",
					  "private_key": "%s",
					  "client_email": "%s",
					  "client_id": "%s"
					}
					""",
					escapeJson(type),
					escapeJson(projectId),
					escapeJson(privateKeyId),
					escapeJson(formattedPrivateKey),
					escapeJson(clientEmail),
					escapeJson(clientId)
			);

			GoogleCredentials credentials = GoogleCredentials.fromStream(
					new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))
			);

			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(credentials)
					.setProjectId(projectId)
					.build();

			FirebaseApp.initializeApp(options);
			log.info("FirebaseApp initialized successfully with projectId: {}", projectId);
		} catch (Exception e) {
			log.error("Failed to initialize FirebaseApp", e);
		}
	}

	private String escapeJson(String input) {
		if (input == null) {
			return "";
		}
		return input.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\r", "\\r")
				.replace("\n", "\\n")
				.replace("\t", "\\t");
	}
}
