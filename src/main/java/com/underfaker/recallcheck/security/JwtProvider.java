package com.underfaker.recallcheck.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 토큰 생성·검증.
 * TODO 기존 com.meta.projectbase.auth.util.JwtUtil 의 구현을 이 클래스로 옮길 것.
 *      (jjwt 0.12.6 기준 — Jwts.builder() / Jwts.parser().verifyWith(key))
 */
@Component
public class JwtProvider {

    @Value("${jwt.secret.key}")
    private String secretKey;

    @Value("${jwt.expiration_time}")
    private long expirationTime;

    /** 액세스 토큰 발급 */
    public String createToken(Long userId, String email, String role) {
        throw new UnsupportedOperationException("TODO: JwtProvider.createToken");
    }

    /** 서명·만료 검증 */
    public boolean validate(String token) {
        throw new UnsupportedOperationException("TODO: JwtProvider.validate");
    }

    /** 토큰에서 사용자 식별자 추출 */
    public Long getUserId(String token) {
        throw new UnsupportedOperationException("TODO: JwtProvider.getUserId");
    }

    public long getExpirationTime() {
        return expirationTime;
    }
}
