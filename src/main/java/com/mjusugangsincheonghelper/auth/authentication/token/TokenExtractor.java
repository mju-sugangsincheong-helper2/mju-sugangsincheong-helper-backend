package com.mjusugangsincheonghelper.auth.authentication.token;

import jakarta.servlet.http.HttpServletRequest;

public interface TokenExtractor {

	String extract(HttpServletRequest request);
}
