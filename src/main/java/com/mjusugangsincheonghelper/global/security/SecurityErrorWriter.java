package com.mjusugangsincheonghelper.global.security;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.ErrorResponseEnvelope;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Security 필터 체인에서 발생하는 인증/인가 오류를
 * 표준 에러 봉투({@link ErrorResponseEnvelope})로 직렬화해 응답한다.
 *
 * <p>컨트롤러 예외는 {@code GlobalExceptionHandler}(@RestControllerAdvice)가 담당하지만,
 * {@code AuthorizationFilter}/{@code ExceptionTranslationFilter}에서 발생한 오류는
 * {@code DispatcherServlet} 바깥이므로 @RestControllerAdvice에 닿지 않는다.
 * 이 유틸리티로 시멘틱(401/403) 응답을 필터 체인에서 직접 내린다.
 *
 * <p>빈이 아닌 정적 유틸리티로, {@code ObjectMapper}를 파라미터로 받는다.
 * ({@code @WebMvcTest} 슬라이스 컨텍스트처럼 전체 빈 정의가 로드되지 않는 환경에서도
 * {@code ObjectMapper}는 Jackson 자동 설정으로 항상 존재하므로 {@code ConsentCheckFilter}와
 * {@code GlobalSecurityConfig} 양쪽에서 안전하게 재사용할 수 있다.)
 */
public final class SecurityErrorWriter {

	private SecurityErrorWriter() {
	}

	/**
	 * {@code errorCode}의 HTTP 상태와 표준 봉투를 응답 본문에 쓴다.
	 * 이미 커밋된 응답에는 쓰지 않는다.
	 */
	public static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
			throws IOException {
		if (response.isCommitted()) {
			return;
		}
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(ErrorResponseEnvelope.from(errorCode)));
		response.getWriter().flush();
	}
}
