package com.underfaker.recallcheck.exception;

import com.underfaker.recallcheck.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * @RestControllerAdvice — 응답 포맷 통일.
 *
 * 예상 못 한 예외는 반드시 스택트레이스를 로그로 남긴다.
 * 로그 없이 삼키면 클라이언트에는 500 만 보이고 원인을 추적할 방법이 사라진다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        log.warn("[업무 예외] {} {} -> {} {}", request.getMethod(), request.getRequestURI(),
                code.getCode(), e.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e,
                                                              HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[검증 실패] {} {} -> {}", request.getMethod(), request.getRequestURI(), detail);

        ErrorCode code = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code.getCode(), detail));
    }

    /** 요청 본문 누락 / JSON 문법 오류 — 클라이언트 잘못이므로 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e,
                                                              HttpServletRequest request) {
        log.warn("[본문 오류] {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        ErrorCode code = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(),
                        "요청 본문이 비어 있거나 JSON 형식이 잘못되었습니다."));
    }

    /** 브라우저 주소창으로 POST 전용 경로를 열면 여기로 온다 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException e,
                                                          HttpServletRequest request) {
        String supported = e.getSupportedHttpMethods() == null ? "" : e.getSupportedHttpMethods().toString();
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        log.warn("[메서드 불일치] {} {} -> 지원 {}", request.getMethod(), request.getRequestURI(), supported);
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(),
                        supported + " 로만 호출할 수 있는 경로입니다. 현재 요청: " + request.getMethod()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e,
                                                            HttpServletRequest request) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        log.warn("[없는 경로] {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("[예상치 못한 예외] {} {} -> {}: {}",
                request.getMethod(), request.getRequestURI(),
                e.getClass().getName(), e.getMessage(), e);

        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }
}
