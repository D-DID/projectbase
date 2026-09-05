package com.underfaker.recallcheck.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** FR-002 로그인 요청 */
public record LoginRequest(

        @NotBlank @Email
        String email,

        @NotBlank
        String password
) {
}
