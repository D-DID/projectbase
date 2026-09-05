package com.underfaker.recallcheck.security;

import com.underfaker.recallcheck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** DB 에서 사용자 로드 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // TODO userRepository.findByEmail → org.springframework.security.core.userdetails.User 로 변환
        throw new UnsupportedOperationException("TODO: loadUserByUsername");
    }
}
