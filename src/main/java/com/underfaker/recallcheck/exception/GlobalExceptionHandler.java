package com.underfaker.recallcheck.exception;

import com.underfaker.recallcheck.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[검증 실패] {} {} -> {}", request.getMethod(), request.getRequestURI(), detail);

        ErrorCode code = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), detail));
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
