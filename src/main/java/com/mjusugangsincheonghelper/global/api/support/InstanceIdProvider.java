package com.mjusugangsincheonghelper.global.api.support;

import java.net.InetAddress;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InstanceIdProvider {

	private final String instanceId;

	public InstanceIdProvider() {
		this.instanceId = resolveInstanceId();
	}

	public String getInstanceId() {
		return instanceId;
	}

	private String resolveInstanceId() {
		String id = System.getenv("INSTANCE_ID");
		if (id != null) return id;

		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (Exception ignored) {
			return UUID.randomUUID().toString().substring(0, 8);
		}
	}
}
