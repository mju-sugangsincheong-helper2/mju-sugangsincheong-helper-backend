package com.mjusugangsincheonghelper.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "system_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SystemConfig {

	@Id
	@Column(name = "config_key", length = 100)
	private String configKey;

	@Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
	private String configValue;

	@Enumerated(EnumType.STRING)
	@Column(name = "config_type", nullable = false, length = 20)
	private ConfigType configType;

	@Column(columnDefinition = "TEXT")
	private String description;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	public SystemConfig(String configKey, String configValue, ConfigType configType, String description) {
		this.configKey = configKey;
		this.configValue = configValue;
		this.configType = configType;
		this.description = description;
	}

	public void updateValue(String configValue, String description) {
		this.configValue = configValue;
		if (description != null) {
			this.description = description;
		}
	}
}
