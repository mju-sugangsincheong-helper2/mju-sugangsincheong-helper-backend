package com.mjusugangsincheonghelper.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;

public interface TokenExtractor {

	String extract(HttpServletRequest request);
}
