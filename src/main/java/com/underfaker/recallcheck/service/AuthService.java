package com.underfaker.recallcheck.service;

import com.underfaker.recallcheck.dto.request.LoginRequest;
import com.underfaker.recallcheck.dto.request.SignUpRequest;
import com.underfaker.recallcheck.dto.response.TokenResponse;
import com.underfaker.recallcheck.repository.UserRepository;
import com.underfaker.recallcheck.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입·인증·토큰 발급 (FR-001, FR-002) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** FR-001 회원가입 */
    @Transactional
    public void signUp(SignUpRequest request) {
        // TODO 이메일 중복 검사 → User 생성 → 비밀번호 인코딩 후 저장
        throw new UnsupportedOperationException("TODO: signUp");
    }

    /** FR-002 로그인 */
    public TokenResponse login(LoginRequest request) {
        // TODO 이메일로 조회 → 비밀번호 매칭 → JwtProvider 로 토큰 발급
        throw new UnsupportedOperationException("TODO: login");
    }
}
