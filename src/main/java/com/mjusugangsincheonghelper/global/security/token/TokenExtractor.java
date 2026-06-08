package com.mjusugangsincheonghelper.global.security.token;

import jakarta.servlet.http.HttpServletRequest;

public interface TokenExtractor {

	String extract(HttpServletRequest request);
}
