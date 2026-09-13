package com.underfaker.recallcheck.exception;

import org.springframework.http.HttpStatus;

/** 에러코드·메시지·HTTP 상태 정의 */
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 오류가 발생했습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C003", "지원하지 않는 HTTP 메서드입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "존재하지 않는 경로입니다."),
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "C005", "아직 구현되지 않은 기능입니다."),

    // 인증 (FR-001, FR-002)
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "A001", "이미 사용 중인 이메일입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "A002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 토큰입니다. 다시 로그인해 주세요."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "A004", "로그인이 필요합니다. 인증 토큰을 확인해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A005", "접근 권한이 없습니다."),

    // 검증 (FR-003 ~ FR-015)
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "V001", "검증 요청을 찾을 수 없습니다."),
    UNSUPPORTED_IMAGE(HttpStatus.BAD_REQUEST, "V002", "지원하지 않는 이미지 형식입니다."),
    PAGE_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "V003", "상품 페이지를 불러오지 못했습니다."),
    EXTRACTION_EMPTY(HttpStatus.OK, "V004", "제품 정보를 추출하지 못했습니다. 직접 입력해 주세요."),

    // 외부 연동 (FR-009, FR-016)
    OPENAPI_CALL_FAILED(HttpStatus.BAD_GATEWAY, "E001", "공공데이터 API 호출에 실패했습니다."),
    OCR_CALL_FAILED(HttpStatus.BAD_GATEWAY, "E002", "OCR 처리에 실패했습니다."),
    RECALL_NOT_FOUND(HttpStatus.NOT_FOUND, "E003", "리콜 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
