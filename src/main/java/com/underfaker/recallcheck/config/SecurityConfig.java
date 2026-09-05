package com.underfaker.recallcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 인증·인가 정책, 필터 체인 등록.
 * TODO 기존 com.meta.projectbase.auth.config.SecurityConfig 의 SecurityFilterChain 설정을 이식할 것.
 *      (JwtAuthenticationFilter 등록, permitAll 경로: /api/auth/**, /api/recalls/**)
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
