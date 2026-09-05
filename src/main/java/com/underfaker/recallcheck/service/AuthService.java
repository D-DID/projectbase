package com.underfaker.recallcheck.service;

import com.underfaker.recallcheck.dto.request.LoginRequest;
import com.underfaker.recallcheck.dto.request.SignUpRequest;
import com.underfaker.recallcheck.dto.response.TokenResponse;
import com.underfaker.recallcheck.entity.User;
import com.underfaker.recallcheck.entity.enums.Role;
import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
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
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .username(request.username())
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }

    /**
     * FR-002 로그인.
     *
     * 이메일이 없을 때와 비밀번호가 틀렸을 때를 같은 에러로 응답한다.
     * 구분해서 알려주면 가입된 이메일 목록이 노출된다.
     */
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String role = user.getRole() == null ? Role.USER.name() : user.getRole().name();
        String token = jwtProvider.createToken(user.getId(), user.getEmail(), role);

        return TokenResponse.bearer(token, jwtProvider.getExpirationTime());
    }
}
