package com.underfaker.recallcheck.common;

/** 성공·실패 응답 표준 포맷 */
public record ApiResponse<T>(

        boolean success,
        T data,
        String code,
        String message
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, code, message);
    }
}
