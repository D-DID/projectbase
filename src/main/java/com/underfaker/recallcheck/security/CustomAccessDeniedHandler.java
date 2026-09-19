package com.underfaker.recallcheck.security;

import com.underfaker.recallcheck.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증은 되었으나 권한이 부족한 요청에 대한 403 응답.
 * 예: 일반 사용자가 /api/admin/** (ROLE_ADMIN 필요) 를 호출한 경우.
 *
 * 401(인증 안 됨)과 403(권한 없음)을 구분해서 내려줘야
 * 프론트가 "로그인 화면으로 보낼지" / "권한 없음 안내를 띄울지" 판단할 수 있다.
 */
@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        ErrorCode code = ErrorCode.ACCESS_DENIED;
        log.warn("[권한 부족] {} {} -> {}", request.getMethod(), request.getRequestURI(),
                accessDeniedException.getMessage());

        CustomAuthenticationEntryPoint.writeJson(response, code);
    }
}