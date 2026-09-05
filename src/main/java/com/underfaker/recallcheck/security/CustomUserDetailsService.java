package com.underfaker.recallcheck.security;

import com.underfaker.recallcheck.entity.User;
import com.underfaker.recallcheck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/** DB 에서 사용자 로드 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));

        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole() == null ? "USER" : user.getRole().name());
    }

    /**
     * 현재 로그인한 사용자의 user_id.
     * 인증되지 않은 요청이면 null 을 반환한다.
     */
    public static Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return (principal instanceof CustomUserDetails details) ? details.getId() : null;
    }

    /**
     * 인증 주체. user_id 를 함께 들고 다녀서 서비스 계층이 이메일로 다시 조회하지 않아도 되게 한다.
     */
    public static class CustomUserDetails implements UserDetails {

        private final Long id;
        private final String email;
        private final String password;
        private final String role;

        public CustomUserDetails(Long id, String email, String password, String role) {
            this.id = id;
            this.email = email;
            this.password = password;
            this.role = role;
        }

        public Long getId() {
            return id;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public String getUsername() {
            return email;
        }
    }
}
