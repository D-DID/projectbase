package com.underfaker.recallcheck.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-001 회원가입 요청 */
public record SignUpRequest(

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, max = 64)
        String password,

        @NotBlank @Size(max = 100)
        String username
) {
}
