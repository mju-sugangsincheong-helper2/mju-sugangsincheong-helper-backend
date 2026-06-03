package com.mjusugangsincheonghelper.global.api.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientInfoExtractor {

	public String resolveIpAddress(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwarded)) {
			return forwarded.split(",")[0].trim();
		}
		String realIp = request.getHeader("X-Real-IP");
		if (StringUtils.hasText(realIp)) {
			return realIp;
		}
		return request.getRemoteAddr();
	}

	public String resolveUserAgent(HttpServletRequest request) {
		return request.getHeader("User-Agent");
	}
}
