package com.underfaker.recallcheck.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** FR-003 URL 입력 검증 요청 */
public record UrlVerifyRequest(

        @NotBlank
        @Size(max = 1000)
        @Pattern(regexp = "^https?://.+", message = "http 또는 https 로 시작하는 URL 이어야 합니다.")
        String url
) {
}
