package com.underfaker.recallcheck.security;

import com.underfaker.recallcheck.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청에 대한 401 응답 (토큰 없음 · 만료 · 서명 불일치).
 *
 * 이 핸들러가 없으면 스프링 시큐리티 기본 응답
 * {"timestamp":...,"status":401,"error":"Unauthorized","path":...} 이 나가서
 * 우리 표준 포맷(ApiResponse)과 응답 구조가 두 갈래로 갈린다.
 * 프론트가 두 포맷을 모두 처리해야 하므로 여기서 통일한다.
 *
 * 참고: 시큐리티 필터는 DispatcherServlet 앞에서 동작하므로
 * GlobalExceptionHandler(@RestControllerAdvice)가 잡지 못한다. 그래서 별도 핸들러가 필요하다.
 */
@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorCode code = ErrorCode.UNAUTHENTICATED;
        log.warn("[인증 실패] {} {} -> {}", request.getMethod(), request.getRequestURI(),
                authException.getMessage());

        writeJson(response, code);
    }

    /**
     * ObjectMapper 를 쓰지 않고 직접 조립한다.
     * 클래스패스에 Jackson 2(com.fasterxml)와 Jackson 3(tools.jackson)이 함께 올라와 있어
     * 어느 쪽 ObjectMapper 가 주입될지 확정하기 어렵고, 고정 문자열이라 직렬화가 필요 없다.
     */
    static void writeJson(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String body = "{\"success\":false,\"data\":null,\"code\":\""
                + code.getCode() + "\",\"message\":\"" + code.getMessage() + "\"}";

        response.getWriter().write(body);
    }
}
