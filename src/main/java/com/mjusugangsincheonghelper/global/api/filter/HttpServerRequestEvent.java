package com.mjusugangsincheonghelper.global.api.filter;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * URL 기준 콜스택 측정의 창(window) 이벤트. JFR 파일 안에 요청 구간을 박아두는 역할만 하며,
 * 콜스택 본체는 JDK 내장 이벤트(ExecutionSample, SocketRead 등)로 조인한다.
 *
 * <p>인터페이스 v1 동결 (같은 exp 디렉토리의 tools/CONTRACT.md):
 * 이름·필드 추가는 additive-only. 이 계약에만 스크립트·분석기가 의존한다.</p>
 */
@Name("http.server.request")
@Category({"HTTP", "Server"})
@Label("HTTP Server Request")
@StackTrace(false)
@Enabled(true)
public class HttpServerRequestEvent extends Event {

	@Label("HTTP method")
	public String method;

	@Label("Route template")
	public String route;

	@Label("Query string")
	public String query;

	@Label("JFR tag")
	public String jfrTag;

	@Label("Request ID")
	public String requestId;

	@Label("Response status")
	public int status;
}
