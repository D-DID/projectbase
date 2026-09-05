package com.underfaker.recallcheck.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * 토큰 생성·검증 (jjwt 0.12.6).
 *
 * 시크릿 키는 Base64 로 인코딩된 값을 받아 디코드해서 쓴다.
 * HS256 은 최소 256비트(32바이트)가 필요하므로, 디코드 결과가 32바이트 미만이면
 * Keys.hmacShaKeyFor 가 WeakKeyException 을 던진다.
 */
@Component
public class JwtProvider {

    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Bearer ";

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long expirationTime;

    public JwtProvider(@Value("${jwt.secret.key}") String secretKey,
                       @Value("${jwt.expiration_time}") long expirationTime) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.expirationTime = expirationTime;
    }

    /** 액세스 토큰 발급. subject 에는 user_id 를 담는다. */
    public String createToken(Long userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationTime))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증. 예외를 밖으로 던지지 않고 false 로 정리한다. */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public String getEmail(String token) {
        return parse(token).get(CLAIM_EMAIL, String.class);
    }

    public String getRole(String token) {
        return parse(token).get(CLAIM_ROLE, String.class);
    }

    /** "Bearer xxx" 헤더에서 토큰만 잘라낸다. 형식이 아니면 null. */
    public String resolveToken(String header) {
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        return header.substring(PREFIX.length()).trim();
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
